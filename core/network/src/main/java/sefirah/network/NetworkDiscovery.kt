package sefirah.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdServiceInfo
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readUTF8Line
import io.ktor.utils.io.streams.writerUTF8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import sefirah.common.util.checkLocationPermissions
import sefirah.data.repository.AppRepository
import sefirah.database.model.DeviceNetworkCrossRef
import sefirah.database.model.NetworkEntity
import sefirah.database.model.toDomain
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.UdpBroadcast
import sefirah.domain.repository.SocketFactory
import sefirah.network.NetworkService.Companion.REMOTE_INFO
import sefirah.network.util.MessageSerializer
import javax.inject.Inject


class NetworkDiscovery @Inject constructor(
    private val nsdService: NsdService,
    private val messageSerializer: MessageSerializer,
    private val appRepository: AppRepository,
    private val socketFactory: SocketFactory,
    private val context: Context,
) {
    private lateinit var connectivityManager: ConnectivityManager
    private var isRegistered = false
    private var udpSocket: BoundDatagramSocket? = null
    private var broadcasterJob: Job? = null
    private var listenerJob: Job? = null
    private var cleanupJob: Job? = null
    private var pairedDeviceListener: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO) + SupervisorJob()
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    // Capture the stateflow from the service as the data updates
    private val services: StateFlow<List<NsdServiceInfo>> = nsdService.services
    private var port: Int = 8689
    private val udpPort: Int = 8689

    enum class NetworkAction {
        SAVE_NETWORK,
        START_DEVICE_DISCOVERY,
        NONE
    }

    var action = NetworkAction.NONE

    fun register(networkAction: NetworkAction) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && action == NetworkAction.SAVE_NETWORK) saveCurrentNetworkAsTrusted()
        action = networkAction
        Log.d(TAG, "Registering network action: $action")
        if (isRegistered) unregister()

        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isRegistered = true
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun unregister() {
        if (!isRegistered) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    private val networkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                Log.d(TAG, "Network callback Received: $action")
                val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                when (action) {
                    NetworkAction.SAVE_NETWORK -> saveCurrentNetworkAsTrusted(wifiInfo)
                    NetworkAction.START_DEVICE_DISCOVERY -> deviceDiscoveryCallback(wifiInfo)
                    NetworkAction.NONE -> { /* Do nothing */ }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network Lost")
                stopDefaultListener()
            }
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network callback Received")
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                when (action) {
                    NetworkAction.SAVE_NETWORK -> saveCurrentNetworkAsTrusted(wifiInfo)
                    NetworkAction.START_DEVICE_DISCOVERY -> deviceDiscoveryCallback(wifiInfo)
                    NetworkAction.NONE -> { /* Do nothing */ }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network Lost")
                stopDefaultListener()
            }
        }
    }

    private fun startNSDDiscovery(localDevice: LocalDevice) {
        nsdService.startDiscovery()
        scope.launch {
            services.collectLatest { serviceInfoList ->
                val nsdDevices = serviceInfoList.mapNotNull { serviceInfo ->
                    try {
                        createDiscoveredDevice(serviceInfo)?.takeIf {
                            it.deviceId.isNotBlank() && 
                            it.publicKey.isNotBlank() && 
                            it.ipAddresses.isNotEmpty()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing service info", e)
                        null
                    }
                }

                _discoveredDevices.update { currentList ->
                    // Merge NSD devices with existing UDP devices
                    val udpDevices = currentList.filter { it.source == DiscoverySource.UDP }
                    val merged = (udpDevices + nsdDevices)
                        .distinctBy { it.deviceId }
                        .sortedByDescending { it.timestamp }
                    
                    merged.filter { device ->
                        device.deviceId != localDevice.deviceId && 
                        device.publicKey.isNotBlank()
                    }
                }
            }
        }
    }

    fun startDiscovery() {
        scope.launch {
            Log.d(TAG, "Starting discovery")
            val localDevice = appRepository.getLocalDevice().toDomain()
            startBroadcasting(localDevice)
            startNSDDiscovery(localDevice)
            // Start listening on default port
            udpSocket?.let { socket ->
                val defaultPort = (socket.localAddress as? InetSocketAddress)?.port
                if (defaultPort != null) {
                    startDeviceListener(localDevice)
                }
            }

            startCleanupJob()
        }
    }

    private fun deviceDiscoveryCallback(wifiInfo: WifiInfo?) {
        CoroutineScope(Dispatchers.IO).launch {
            if (wifiInfo != null && wifiInfo.ssid != UNKNOWN_SSID && wifiInfo.networkId != -1) {
                val knownNetwork = appRepository.isNetworkEnabled(wifiInfo.ssid.removeSurrounding("\""))
                Log.d(TAG, "is Network Enabled: ${knownNetwork}, pairedDeviceListener: ${pairedDeviceListener?.isActive}, listenerJobs: ${listenerJob?.isActive}")
                if (knownNetwork &&
                    (pairedDeviceListener?.isActive == false || pairedDeviceListener == null) &&
                    listenerJob?.isActive == false || listenerJob == null) {
                    Log.d(TAG, "Starting paired device listener on port $port")
                    startPairedDeviceListener()
                }
            }
        }
    }

    private fun startPairedDeviceListener() {
        if (udpSocket == null) {
            try {
                udpSocket = socketFactory.udpSocket(udpPort)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create UDP socket on port $port", e)
                return
            }
        }

        pairedDeviceListener = scope.launch {
            try {
                val localDevice = appRepository.getLocalDevice().toDomain()
                val lastConnectedDevice = appRepository.getLastConnectedDevice() ?: return@launch

                while (isActive) {
                    val datagram = udpSocket?.receive() ?: continue
                    val udpBroadcast = datagram.packet.readUTF8Line()?.let {
                        messageSerializer.deserialize(it) as UdpBroadcast
                    } ?: continue

                    if (udpBroadcast.deviceId == localDevice.deviceId) continue

                    if (udpBroadcast.deviceId == lastConnectedDevice.deviceId) {
                        Log.d(TAG, "Paired device discovered: ${udpBroadcast.deviceId}")
                        initiateConnection(lastConnectedDevice.deviceId)
                        unregister()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in paired device listener", e)
            }
        }
    }

    private fun startBroadcasting(localDevice: LocalDevice) {
        if (udpSocket == null) {
            try {
                udpSocket = socketFactory.udpSocket(udpPort)
                Log.d(TAG, "UDP socket created successfully on port $udpPort")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create UDP socket on port $udpPort", e)
                return
            }
        }
        
        broadcasterJob = scope.launch {
            Log.d(TAG, "Broadcaster started")
            try {
                val udpBroadcast = UdpBroadcast(
                    deviceId = localDevice.deviceId,
                    deviceName = localDevice.deviceName,
                    publicKey = localDevice.publicKey,
                    timestamp = null,
                )
                nsdService.advertiseService(udpBroadcast, port)

                val broadcastList = appRepository.getAllCustomIpFlow().first().toMutableList().also {
                    it.add("255.255.255.255")
                    Log.d(TAG, "Broadcasting to ${it.size} targets")
                }

                while (isActive) {
                    udpBroadcast.timestamp = System.currentTimeMillis()
                    val serializedMessage = messageSerializer.serialize(udpBroadcast)

                    broadcastList.forEach { ipAddress ->
                        try {
                            if (udpSocket?.isClosed == true) {
                                Log.w(TAG, "Recreating closed socket")
                                udpSocket = socketFactory.udpSocket(udpPort)
                            }

                            val bytePacket = buildPacket {
                                writerUTF8().write(serializedMessage)
                            }

                            udpSocket?.send(Datagram(
                                bytePacket,
                                InetSocketAddress(ipAddress, 8689)
                            ))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to $ipAddress: ${e.message?.take(30)}")
                        }
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcaster failed: ${e.message}")
            } finally {
                Log.d(TAG, "Broadcaster stopped")
            }
        }
    }

    private fun startDeviceListener(localDevice: LocalDevice) {
        try {
            if (udpSocket == null) {
                try {
                    udpSocket = socketFactory.udpSocket(udpPort)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create UDP socket on port $port", e)
                    return
                }
            }

            listenerJob = scope.launch {
                try {
                    while (isActive) {
                        val datagram = udpSocket!!.receive()
                        val udpBroadcast = datagram.packet.readUTF8Line()?.let {
                            messageSerializer.deserialize(it) as UdpBroadcast
                        } ?: continue
                        if (udpBroadcast.deviceId == localDevice.deviceId) continue
                        
                        val discoveredDevice = DiscoveredDevice(
                            deviceId = udpBroadcast.deviceId,
                            deviceName = udpBroadcast.deviceName,
                            publicKey = udpBroadcast.publicKey,
                            port = udpBroadcast.port!!,
                            ipAddresses = udpBroadcast.ipAddresses,
                            timestamp = System.currentTimeMillis(),
                            source = DiscoverySource.UDP
                        )
                        updateDiscoveredDevices(discoveredDevice)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in device listener on port $port", e)
                }
            }
            Log.d(TAG, "Started UDP listener on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create UDP listener on port $port", e)
        }
    }

    private fun startCleanupJob() {
        cleanupJob = scope.launch {
            while (isActive) {
                cleanupStaleDevices()
                delay(5000)
            }
        }
    }

    private fun initiateConnection(deviceId: String) {
        val workRequest = OneTimeWorkRequestBuilder<NetworkWorker>()
            .setInputData(workDataOf(REMOTE_INFO to deviceId))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                NETWORK_WORKER_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        stopDefaultListener()
    }

    fun stopDiscovery() {
        action = NetworkAction.NONE
        broadcasterJob?.cancel()
        broadcasterJob = null
        listenerJob?.cancel()
        cleanupJob?.cancel()
        nsdService.stopDiscovery()
        nsdService.stopAdvertisingService()

        try {
            udpSocket?.close()
            udpSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UDP socket", e)
        }

        _discoveredDevices.value = emptyList()
    }

    private fun stopDefaultListener() {
        try {
            udpSocket?.close()
            udpSocket = null
            nsdService.stopAdvertisingService()
            listenerJob?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listener", e)
        }
    }

    private fun updateDiscoveredDevices(newDevice: DiscoveredDevice) {
        _discoveredDevices.update { currentList ->
            val existing = currentList.find { it.deviceId == newDevice.deviceId }
            when {
                existing?.source == DiscoverySource.NSD -> currentList
                else -> currentList.filterNot { it.deviceId == newDevice.deviceId } + newDevice
            }
        }
    }

    private fun cleanupStaleDevices() {
        val currentTime = System.currentTimeMillis()
        _discoveredDevices.update { currentList ->
            currentList.filterNot { device ->
                device.source == DiscoverySource.UDP && 
                (currentTime - device.timestamp) > DEVICE_TIMEOUT
            }
        }
    }

    fun saveCurrentNetworkAsTrusted(wifiInfo: WifiInfo? = null) {
        if (!checkLocationPermissions(context)) return
        val ssid: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && wifiInfo != null) {
            ssid = wifiInfo.ssid.removeSurrounding("\"")
        } else {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            wifiManager.dhcpInfo.gateway
            if (connectionInfo.supplicantState != SupplicantState.COMPLETED) return
            ssid = connectionInfo.ssid.removeSurrounding("\"")
        }
        Log.d(TAG, "SSID: $ssid")
        when {
            ssid.equals(UNKNOWN_SSID, ignoreCase = true) -> return
            ssid.isBlank() -> return
        }
        action = NetworkAction.NONE
        unregister()
        scope.launch {
            appRepository.addNetwork(NetworkEntity(ssid = ssid, isEnabled = true))
            appRepository.addNetworkToDevice(DeviceNetworkCrossRef(deviceId = appRepository.getLastConnectedDevice()!!.deviceId, ssid = ssid))
        }
    }

    companion object {
        private const val TAG = "NetworkDiscovery"
        private const val NETWORK_WORKER_NAME = "network_connection_worker"
        private const val UNKNOWN_SSID = "<unknown ssid>"
        private const val DEVICE_TIMEOUT = 3000L // 3 seconds timeout
    }
}
