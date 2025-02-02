package sefirah.network

import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.clipboard.ClipboardHandler
import sefirah.common.notifications.NotificationCenter
import sefirah.data.repository.AppRepository
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DeviceStatus
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.SocketType
import sefirah.domain.repository.PlaybackRepository
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.media.MediaHandler
import sefirah.network.NetworkDiscovery.NetworkAction
import sefirah.network.extensions.handleDeviceInfo
import sefirah.network.extensions.handleMessage
import sefirah.network.extensions.setNotification
import sefirah.network.sftp.SftpServer
import sefirah.network.util.ECDHHelper
import sefirah.network.util.MessageSerializer
import sefirah.network.util.getInstalledApps
import sefirah.notification.NotificationHandler
import javax.inject.Inject

@AndroidEntryPoint
class NetworkService : Service() {
    @Inject lateinit var socketFactory: SocketFactory
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var messageSerializer: MessageSerializer
    @Inject lateinit var notificationHandler: NotificationHandler
    @Inject lateinit var notificationCenter: NotificationCenter
    @Inject lateinit var clipboardHandler: ClipboardHandler
    @Inject lateinit var networkDiscovery: NetworkDiscovery
    @Inject lateinit var mediaHandler: MediaHandler
    @Inject lateinit var sftpServer: SftpServer
    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var playbackRepository: PlaybackRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): NetworkService = this@NetworkService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var connectivityManager: ConnectivityManager

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var lastBatteryLevel: Int? = null

    private var socket: Socket? = null
    private var writeChannel: ByteWriteChannel? = null
    private var readChannel: ByteReadChannel? = null

    private var connectedDevice: RemoteDevice? = null
    private var deviceName: String? = null
    private var connectedIpAddress: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.name -> {
                val remoteInfo = intent.getParcelableExtra<RemoteDevice>(REMOTE_INFO)
                if (remoteInfo != null && _connectionState.value.isDisconnected) {
                    _connectionState.value = ConnectionState.Connecting
                    deviceName = remoteInfo.deviceName
                    start(remoteInfo)
                }
            }
            Actions.STOP.name -> {
                stop(true)
            }
        }
        return START_NOT_STICKY
    }

    private fun start(remoteInfo: RemoteDevice) {
        _connectionState.value = ConnectionState.Connecting
        scope.launch {
            try {
                var connected = false
                if (remoteInfo.prefAddress != null) {
                    connected = initializeConnection(remoteInfo.prefAddress!!, remoteInfo.port)
                } else {
                    for (ipAddress in remoteInfo.ipAddresses) {
                        if (initializeConnection(ipAddress, remoteInfo.port)) {
                            connected = true
                            break
                        }
                    }
                }

                if (!connected) {
                    Log.e(TAG, "All connection attempts failed")
                    _connectionState.value = ConnectionState.Error("Device not reachable")
                    delay(100)
                    stop(false)
                    return@launch
                }

                // Send initial device info for verification
                sendDeviceInfo(remoteInfo)

                // Wait for device info
                withTimeoutOrNull(60000) { // 30 seconds timeout
                    readChannel?.readUTF8Line()?.let { jsonMessage ->
//                        Log.d(TAG, "Raw received data: $jsonMessage")
                        val deviceInfo = messageSerializer.deserialize(jsonMessage) as? DeviceInfo
                        if (deviceInfo == null) {
                            Log.e(TAG, "Invalid device info received")
                            return@withTimeoutOrNull null
                        }
                        connectedDevice = remoteInfo
                        handleDeviceInfo(deviceInfo, remoteInfo, connectedIpAddress!!)
                        return@withTimeoutOrNull Unit
                    }
                } ?: run {
                    Log.e(TAG, "Timeout waiting for device info")
                    _connectionState.value = ConnectionState.Disconnected()
                    stop(false)
                    return@launch
                }
                _connectionState.value = ConnectionState.Connected
                startListening()
                if (remoteInfo.avatar == null) {
                    sendInstalledApps()
                }
                // Setup complete
                finalizeConnection()
            } catch (e: Exception) {
                Log.e(TAG, "Error in connecting", e)
                _connectionState.value = ConnectionState.Disconnected()
                stop(false)
            }
        }
    }

    private suspend fun initializeConnection(ipAddress: String, port: Int): Boolean {
        try {
            socket = socketFactory.tcpClientSocket(SocketType.DEFAULT, ipAddress, port)
            if (socket != null) {
                writeChannel = socket?.openWriteChannel()
                readChannel = socket?.openReadChannel()
                connectedIpAddress = ipAddress
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun sendDeviceInfo(remoteInfo: RemoteDevice) {
        val localDevice = appRepository.getLocalDevice()

        // Generate nonce and proof
        val nonce = ECDHHelper.generateNonce()
        val sharedSecret = ECDHHelper.deriveSharedSecret(
            localDevice.privateKey,
            remoteInfo.publicKey
        )
        val proof = ECDHHelper.generateProof(sharedSecret, nonce)

        sendMessage(DeviceInfo(
            deviceId = localDevice.deviceId,
            deviceName = localDevice.deviceName,
            publicKey = localDevice.publicKey,
            avatar = localDevice.wallpaperBase64,
            nonce = nonce,
            proof = proof
        ))
    }

    private suspend fun sendInstalledApps() {
        getInstalledApps(packageManager).forEach { app ->
            sendMessage(app)
            delay(10)
        }
    }

    private suspend fun finalizeConnection() {
        networkDiscovery.register(NetworkAction.SAVE_NETWORK)
        sendDeviceStatus()
        notificationHandler.sendActiveNotifications()
        clipboardHandler.start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && preferencesRepository.readRemoteStorageSettings().first()) {
            sftpServer.start()?.let { sendMessage(it) }
        }
        networkDiscovery.unregister()
    }

    fun stop(forcedStop: Boolean) {
        if (forcedStop) {
            networkDiscovery.unregister()
            _connectionState.value = ConnectionState.Disconnected(true)
        } else {
            networkDiscovery.register(NetworkAction.START_DEVICE_DISCOVERY)
            _connectionState.value = ConnectionState.Disconnected()
        }
        lastBatteryLevel = null
        sftpServer.stop()
        mediaHandler.release()
        writeChannel?.close()
        socket?.close()
    }

    private val mutex = Mutex() // Mutex to control write access
    suspend fun sendMessage(message: SocketMessage) {
        // Only one coroutine at a time can acquire the lock and send the message
        mutex.withLock {
            try {
                if (socket?.isClosed == false) {
                    writeChannel?.let { channel ->
                        val jsonMessage = messageSerializer.serialize(message)
                        channel.writeStringUtf8("$jsonMessage\n") // Add newline to separate messages
                        channel.flush()

                        Log.d(TAG, "Message sent")
                    } ?: run {
                        Log.e(TAG, "Write channel is not available")
                    }
                } else {
                    // Disconnected
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
            }
        }
    }

    private fun startListening() {
        scope.launch {
            try {
                Log.d(TAG, "listening started")
                readChannel?.let { channel ->
                    while (!channel.isClosedForRead) {
                        try {
                            // Read the incoming data as a line
                            val receivedData = channel.readUTF8Line()
                            receivedData?.let { jsonMessage ->
                                Log.d(TAG, "Raw received data: $jsonMessage")
                                messageSerializer.deserialize(jsonMessage).also { socketMessage ->
                                    socketMessage?.let { handleMessage(it) }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Error while receiving data")
                            e.printStackTrace()
                            break
                        }
                    }
                } ?: run {
                   stop(false)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Session error")
                e.printStackTrace()
            } finally {
                Log.d(TAG, "Session closed")
                if (_connectionState.value == ConnectionState.Connected) {
                    stop(false)
                }
            }
        }
    }

    private fun sendDeviceStatus() {
        scope.launch {
            try {
                val deviceStatus = getDeviceStatus()
                val currentBatteryLevel = deviceStatus.batteryStatus
                // Only send the status if the battery level has changed
                if (currentBatteryLevel != lastBatteryLevel) {
                    lastBatteryLevel = currentBatteryLevel
                    sendMessage(deviceStatus)
                }
            } catch (e: Exception) {
                Log.e("WebSocketService", "Failed to send device status", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sftpServer.initialize()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // Add observer for connection state changes
        scope.launch {
            _connectionState.collect { state ->
                setNotification(state, deviceName)
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (_connectionState.value == ConnectionState.Connected) {
                sendDeviceStatus()
            }
        }
    }

    private fun getDeviceStatus(): DeviceStatus {
        val batteryStatus: Int? =
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

        val isCharging = batteryStatus != null &&
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

//        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
//        val wifi = wifiManager.isWifiEnabled

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetooth = bluetoothManager.adapter.isEnabled

        return DeviceStatus(
            batteryStatus = batteryStatus,
            bluetoothStatus = bluetooth,
            chargingStatus = isCharging
        )
    }

    companion object {
        enum class Actions {
            START,
            STOP,
        }

        const val TAG = "NetworkService"
        const val REMOTE_INFO = "remote_info"
    }
}