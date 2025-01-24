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
import io.ktor.util.encodeBase64
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import sefirah.common.util.checkLocationPermissions
import sefirah.data.repository.AppRepository
import sefirah.database.model.NetworkEntity
import sefirah.database.model.toDomain
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.UdpBroadcast
import sefirah.domain.repository.SocketFactory
import sefirah.network.NetworkService.Companion.REMOTE_INFO
import sefirah.network.util.CryptoUtils
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
    private var defaultUdpSocket: BoundDatagramSocket? = null
    private val udpSockets = mutableMapOf<Int, BoundDatagramSocket>()
    private var broadcasterJob: Job? = null
    private var listenerJobs = mutableMapOf<Int, Job>()
    private var cleanupJob: Job? = null
    private var pairedDeviceListener: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO) + SupervisorJob()
    private val _discoveredDevices = MutableStateFlow<List<UdpBroadcast>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    // Capture the stateflow from the service as the data updates
    private val services: StateFlow<List<NsdServiceInfo>> = nsdService.services
    private var port: Int = 8689

    enum class NetworkAction {
        SAVE_NETWORK,
        START_DEVICE_DISCOVERY,
        NONE
    }

    var action = NetworkAction.NONE

    init {
        for (port in PORT_RANGE) {
            try {
                defaultUdpSocket = socketFactory.udpSocket(port)
                nsdService.advertiseService(port)
                break
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create UDP socket on port $port", e)
            }
        }
    }

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
        }
    }

    private fun startNSDDiscovery(localDevice: LocalDevice) {
        nsdService.startDiscovery()
        scope.launch {
            services.collectLatest { serviceInfoList ->
                serviceInfoList.forEach { serviceInfo ->
                    val port = serviceInfo.port
                    if (port > 0) {
                        startDeviceListener(port, localDevice)
                    }
                }
            }
        }
    }

    fun saveCurrentNetworkAsTrusted(wifiInfo: WifiInfo? = null) {
        if (!checkLocationPermissions(context)) return
        val networkId: Int?
        val ssid: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && wifiInfo != null) {
            networkId = wifiInfo.networkId
            ssid = wifiInfo.ssid
        } else {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo.supplicantState != SupplicantState.COMPLETED) return
            networkId = connectionInfo.networkId
            ssid = connectionInfo.ssid
        }
        Log.d(TAG, "NetworkId: $networkId, SSID: $ssid")
        when {
            ssid.equals(UNKNOWN_SSID, ignoreCase = true) || networkId == -1 -> return
            ssid.isBlank() -> return
        }
        action = NetworkAction.NONE
        unregister()
        scope.launch {
            appRepository.addNetwork(NetworkEntity(id = networkId, ssid = ssid.removeSurrounding("\""), isEnabled = true))
        }
    }

    fun startDiscovery() {
        scope.launch {
            val localDevice = appRepository.getLocalDevice().toDomain()
            startBroadcasting(localDevice)
            
            // Start listening on default port
            defaultUdpSocket?.let { socket ->
                val defaultPort = (socket.localAddress as? InetSocketAddress)?.port
                if (defaultPort != null) {
                    startDeviceListener(defaultPort, localDevice)
                }
            }
            
            // Start NSD discovery and listen for services
            startNSDDiscovery(localDevice)
            startCleanupJob()
        }
    }

    private fun deviceDiscoveryCallback(wifiInfo: WifiInfo?) {
        CoroutineScope(Dispatchers.IO).launch {
            if (wifiInfo != null && wifiInfo.ssid != UNKNOWN_SSID && wifiInfo.networkId != -1) {
                val knownNetwork = appRepository.getNetwork(wifiInfo.networkId)
                Log.d(TAG, "Known Network: ${knownNetwork?.id}, pairedDeviceListener: ${pairedDeviceListener?.isActive}, listenerJobs: ${listenerJobs[port]?.isActive}")
                if (knownNetwork != null &&
                    (pairedDeviceListener?.isActive == false || pairedDeviceListener == null) &&
                    (listenerJobs[port]?.isActive == false || listenerJobs[port] == null)) {
                    Log.d(TAG, "Starting paired device listener on port $port")
                    startPairedDeviceListener()
                }
            }
        }
    }

    private fun startPairedDeviceListener() {
        // Always try to create a new socket if we don't have one
        if (defaultUdpSocket == null) {
            for (port in PORT_RANGE) {
                try {
                    defaultUdpSocket = socketFactory.udpSocket(port)
                    // Also advertise the service with this port
                    nsdService.advertiseService(port)
                    Log.d(TAG, "Created new UDP socket on port $port")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create UDP socket on port $port", e)
                }
            }
        }

        if (defaultUdpSocket == null) {
            Log.e(TAG, "Failed to create default UDP socket")
            return
        }

        pairedDeviceListener = scope.launch {
            try {
                val localDevice = appRepository.getLocalDevice().toDomain()
                val lastConnectedDevice = appRepository.getLastConnectedDevice() ?: return@launch
                action = NetworkAction.NONE
                unregister()
                while (isActive) {
                    val datagram = defaultUdpSocket?.receive() ?: continue
                    val udpBroadcast = datagram.packet.readUTF8Line()?.let {
                        messageSerializer.deserialize(it) as UdpBroadcast
                    } ?: continue

                    if (udpBroadcast.deviceId == localDevice.deviceId) continue

                    if (udpBroadcast.deviceId == lastConnectedDevice.deviceId) {
                        Log.d(TAG, "Paired device discovered: ${udpBroadcast.deviceId}")
                        initiateConnection(lastConnectedDevice.deviceId)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in paired device listener", e)
            }
        }
    }

    private fun startBroadcasting(localDevice: LocalDevice) {
        broadcasterJob?.cancel()
        broadcasterJob = scope.launch {
            try {
                val cryptoUtils = CryptoUtils(context)
                val certificate = cryptoUtils.getOrCreateCertificate()
                val udpBroadcast = UdpBroadcast(
                    deviceId = localDevice.deviceId,
                    deviceName = localDevice.deviceName,
                    publicKey = localDevice.publicKey,
                    certificate = certificate.encoded.encodeBase64(),
                    timestamp = null,
                )

                while (isActive) {
                    udpBroadcast.timestamp = System.currentTimeMillis()
                    val serializedMessage = messageSerializer.serialize(udpBroadcast)
                    val bytePacket = buildPacket {
                        writerUTF8().write(serializedMessage ?: "")
                    }
                    defaultUdpSocket?.send(Datagram(
                        bytePacket,
                        InetSocketAddress("255.255.255.255", 8689)
                    ))
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcasting", e)
            }
        }
    }

    private fun startDeviceListener(port: Int, localDevice: LocalDevice) {
        // Check if we already have a listener job for this port
        if (listenerJobs.containsKey(port) && listenerJobs[port]?.isActive == true) {
            Log.d(TAG, "Listener already exists and is active for port $port")
            return
        }

        try {
            // Use existing socket if it's the default port, otherwise create new one
            val socket = if (defaultUdpSocket?.localAddress is InetSocketAddress && 
                (defaultUdpSocket?.localAddress as InetSocketAddress).port == port) {
                defaultUdpSocket
            } else {
                socketFactory.udpSocket(port).also { udpSockets[port] = it }
            }
            
            listenerJobs[port] = scope.launch {
                try {
                    while (isActive) {
                        val datagram = socket?.receive() ?: continue
                        val udpBroadcast = datagram.packet.readUTF8Line()?.let {
                            messageSerializer.deserialize(it) as UdpBroadcast
                        } ?: continue
                        if (udpBroadcast.deviceId == localDevice.deviceId) continue
                        updateDiscoveredDevices(udpBroadcast)
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
        broadcasterJob?.cancel()
        listenerJobs.values.forEach { it.cancel() }
        cleanupJob?.cancel()
        nsdService.stopDiscovery()
        nsdService.stopAdvertisingService()
        // Close all sockets
        try {
            defaultUdpSocket?.close()
            defaultUdpSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing default UDP socket", e)
        }

        udpSockets.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing UDP socket", e)
            }
        }
        udpSockets.clear()
        listenerJobs.clear()
        _discoveredDevices.value = emptyList()
    }

    private fun stopDefaultListener() {
        try {
            defaultUdpSocket?.close()
            defaultUdpSocket = null
            nsdService.stopAdvertisingService()
            // Remove the listener job for the default socket
            listenerJobs[PORT_RANGE.first]?.cancel()
            listenerJobs.remove(PORT_RANGE.first)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping default listener", e)
        }
    }

    private fun updateDiscoveredDevices(newDevice: UdpBroadcast) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        
        // Update existing device or add new one
        val existingDeviceIndex = currentDevices.indexOfFirst { it.deviceId == newDevice.deviceId }
        if (existingDeviceIndex != -1) {
            currentDevices[existingDeviceIndex] = newDevice
        } else {
            currentDevices.add(newDevice)
        }
        
        _discoveredDevices.value = currentDevices
    }

    private fun cleanupStaleDevices() {
        val currentTime = System.currentTimeMillis()
        val currentDevices = _discoveredDevices.value.toMutableList()
        
        currentDevices.removeAll { device ->
            val isStale = device.timestamp?.let { timestamp ->
                (currentTime - timestamp) > DEVICE_TIMEOUT
            } ?: true
            isStale
        }
        
        _discoveredDevices.value = currentDevices
    }

    companion object {
        private const val TAG = "NetworkDiscovery"
        private const val NETWORK_WORKER_NAME = "network_connection_worker"
        private const val UNKNOWN_SSID = "<unknown ssid>"
        private val PORT_RANGE = 8689..8690
        private const val DEVICE_TIMEOUT = 3000L // 3 seconds timeout
    }
}