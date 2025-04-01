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
import sefirah.database.AppRepository
import sefirah.database.model.DeviceNetworkCrossRef
import sefirah.database.model.NetworkEntity
import sefirah.database.model.toDomain
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.UdpBroadcast
import sefirah.domain.repository.SocketFactory
import sefirah.network.NetworkService.Companion.REMOTE_INFO
import sefirah.network.util.MessageSerializer
import sefirah.network.util.getDeviceIpAddress
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

    private val services: StateFlow<List<NsdServiceInfo>> = nsdService.services
    private var udpPort: Int = 5149

    enum class NetworkAction {
        SAVE_NETWORK,
        START_DEVICE_DISCOVERY,
        NONE
    }

    var action = NetworkAction.NONE

    fun register(networkAction: NetworkAction) {
        Log.d(TAG, "Registering network action: $networkAction")
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && action == NetworkAction.SAVE_NETWORK) saveCurrentNetworkAsTrusted()
        action = networkAction

        if (isRegistered) unregister()

        if (networkAction == NetworkAction.START_DEVICE_DISCOVERY
            && (pairedDeviceListener?.isActive == false || pairedDeviceListener == null)
            && (listenerJob?.isActive == false || listenerJob == null)) {
            // Enable device listener if hotspot is enabled or location permission is not granted
            if (isHotspotEnabled() || !checkLocationPermissions(context)) {
                Log.d(TAG, "Starting paired device listener")
                startPairedDeviceListener()
                return
            }
        }

        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isRegistered = true
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

    private fun isHotspotEnabled(): Boolean {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            return method.invoke(wifiManager) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check hotspot status", e)
            return false
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

    // for pairing
    fun startDiscovery() {
        scope.launch {
            Log.i(TAG, "Starting discovery")
            if (pairedDeviceListener?.isActive == true) {
                pairedDeviceListener?.cancel()
                pairedDeviceListener = null
            }

            val localDevice = appRepository.getLocalDevice().toDomain()
            startBroadcasting(localDevice)
            startNSDDiscovery(localDevice)
            startDeviceListener(localDevice)
            startCleanupJob()
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

    // Passive wifi discovery callback: proceeds to start the udp listener if we are connected to a known enabled wifi network
    private fun deviceDiscoveryCallback(wifiInfo: WifiInfo?) {
        CoroutineScope(Dispatchers.IO).launch {
            if (wifiInfo != null && wifiInfo.ssid != UNKNOWN_SSID && wifiInfo.networkId != -1) {
                val knownNetwork = appRepository.isNetworkEnabled(wifiInfo.ssid.removeSurrounding("\""))

                if (knownNetwork &&
                    (pairedDeviceListener?.isActive == false || pairedDeviceListener == null) &&
                    listenerJob?.isActive == false || listenerJob == null) {
                    Log.d(TAG, "Starting paired device listener on port ${this@NetworkDiscovery.udpPort}")
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
                Log.e(TAG, "Failed to create UDP socket on port $udpPort", e)
                return
            }
        }

        pairedDeviceListener = scope.launch {
            try {
                val localDevice = appRepository.getLocalDevice().toDomain()
                val lastConnectedDevice = appRepository.getLastConnectedDevice() ?: return@launch
                unregister()
                while (isActive) {
                    val datagram = udpSocket?.receive() ?: continue
                    val udpBroadcast = datagram.packet.readUTF8Line()?.let {
                        messageSerializer.deserialize(it) as UdpBroadcast
                    } ?: continue

                    if (udpBroadcast.deviceId == localDevice.deviceId) continue

                    if (udpBroadcast.deviceId == lastConnectedDevice.deviceId) {
                        // Check if there are any new IP addresses to add
                        val existingAddresses = lastConnectedDevice.ipAddresses.toSet()
                        val addressesToAdd = udpBroadcast.ipAddresses.toSet() - existingAddresses

                        if (addressesToAdd.isNotEmpty()) {
                            try {
                                // Update by adding the new IP addresses
                                val updatedAddresses = existingAddresses + addressesToAdd
                                appRepository.updateIpAddresses(lastConnectedDevice.deviceId, updatedAddresses.toList())
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update IP addresses in database", e)
                            }
                        }
                        initiateConnection(lastConnectedDevice.deviceId)
                        stopPairedDeviceListener()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in paired device listener", e)
            }
        }
    }

    fun stopPairedDeviceListener() {
        try {
            udpSocket?.close()
            udpSocket = null
            pairedDeviceListener?.cancel()
            pairedDeviceListener = null
            action = NetworkAction.NONE
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listener", e)
        }
    }

    private fun startBroadcasting(localDevice: LocalDevice) {
        if (udpSocket == null) {
            try {
                udpSocket = socketFactory.udpSocket(this.udpPort)
                Log.d(TAG, "UDP socket created successfully on port ${this.udpPort}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create UDP socket on port ${this.udpPort}", e)
                return
            }
        }
        
        broadcasterJob = scope.launch {
            Log.d(TAG, "Broadcaster started")
            try {
                val udpBroadcast = UdpBroadcast(
                    ipAddresses = getDeviceIpAddress()?.let { listOf(it) } ?: emptyList(),
                    deviceId = localDevice.deviceId,
                    deviceName = localDevice.deviceName,
                    publicKey = localDevice.publicKey,
                    timestamp = null,
                )
                nsdService.advertiseService(udpBroadcast, this@NetworkDiscovery.udpPort)

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
                                InetSocketAddress(ipAddress, udpPort)
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
                    Log.d(TAG, "Started UDP listener on port $udpPort")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create UDP socket on port $udpPort", e)
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
                    Log.e(TAG, "Error in device listener on port $udpPort", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create UDP listener on port $udpPort", e)
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
            unregister()
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

            if (connectionInfo.supplicantState != SupplicantState.COMPLETED) return
            ssid = connectionInfo.ssid.removeSurrounding("\"")
        }

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
