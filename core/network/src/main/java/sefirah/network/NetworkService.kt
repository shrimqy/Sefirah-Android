package sefirah.network

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.clipboard.ClipboardHandler
import sefirah.common.R
import sefirah.common.notifications.NotificationCenter
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.smsPermissionGranted
import sefirah.communication.sms.SmsHandler
import sefirah.communication.utils.ContactsHelper
import sefirah.communication.utils.TelephonyHelper
import sefirah.database.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DeviceStatus
import sefirah.domain.model.DiscoveryMode
import sefirah.domain.model.PhoneNumber
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.SocketType
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.network.NetworkDiscovery.NetworkAction
import sefirah.network.extensions.ActionHandler
import sefirah.network.extensions.handleDeviceInfo
import sefirah.network.extensions.handleMessage
import sefirah.network.extensions.setNotification
import sefirah.network.sftp.SftpServer
import sefirah.network.util.ECDHHelper
import sefirah.network.util.MessageSerializer
import sefirah.network.util.getInstalledApps
import sefirah.notification.NotificationHandler
import sefirah.presentation.util.drawableToBase64Compressed
import sefirah.projection.media.MediaHandler
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
    @Inject lateinit var smsHandler: SmsHandler
    @Inject lateinit var actionHandler: ActionHandler

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

    private var socket: Socket? = null
    private var writeChannel: ByteWriteChannel? = null
    private var readChannel: ByteReadChannel? = null

    private var connectedDevice: RemoteDevice? = null
    private var deviceName: String? = null
    private var connectedIpAddress: String? = null

    private var deviceStatus: DeviceStatus? = null

    private var connectionJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.name -> {
                val remoteInfo = intent.getParcelableExtra<RemoteDevice>(REMOTE_INFO)
                if (remoteInfo != null && _connectionState.value.isDisconnected) {
                    deviceName = remoteInfo.deviceName
                    _connectionState.value = ConnectionState.Connecting(deviceName)
                    start(remoteInfo)
                } else if (remoteInfo == null && _connectionState.value.isDisconnected) {
                    scope.launch {
                        val lastDevice = appRepository.getLastConnectedDevice()
                        if (lastDevice != null) {
                            deviceName = lastDevice.deviceName
                            _connectionState.value = ConnectionState.Connecting(deviceName)
                            start(lastDevice.toDomain())
                        } else {
                            Log.w(TAG, "No last connected device found for Tasker action")
                        }
                    }
                }
            }
            Actions.STOP.name -> {
                if (connectionJob?.isActive == true) connectionJob?.cancel()
                stop(true)
            }
        }
        return START_NOT_STICKY
    }

    private fun start(remoteInfo: RemoteDevice) {
        try {
            connectionJob = scope.launch {
                actionHandler.clearActions()

                var connected = false
                if (remoteInfo.prefAddress != null) {
                    connected = initializeConnection(remoteInfo.prefAddress!!, remoteInfo.port)
                }
                if (!connected) {
                    val addresses: MutableList<String> = remoteInfo.ipAddresses.toMutableList()
                    addresses.remove(remoteInfo.prefAddress)

                    for (ipAddress in addresses) {
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
                _connectionState.value = withTimeoutOrNull(30000) { // 30 seconds timeout
                    readChannel?.readUTF8Line()?.let { jsonMessage ->
                        val message = messageSerializer.deserialize(jsonMessage)
                        if (message == null) {
                            Log.e(TAG, "Invalid device info received")
                            return@withTimeoutOrNull ConnectionState.Error(getString(R.string.connection_error))
                        }
                        when (message) {
                            is DeviceInfo -> {
                                connectedDevice = remoteInfo
                                handleDeviceInfo(message, remoteInfo, connectedIpAddress!!)
                                return@withTimeoutOrNull ConnectionState.Connected
                            }

                            else -> {
                                Log.w(TAG, "Authentication rejected")
                                return@withTimeoutOrNull ConnectionState.Error(getString(R.string.connection_error))
                            }
                        }
                    }
                    null
                } ?: run {
                    Log.e(TAG, "Timeout waiting for device info")
                    stop(false)
                    _connectionState.value = ConnectionState.Error(getString(R.string.connection_error))
                    return@launch
                }

                if (_connectionState.value.isConnected) {
                    startListening()
                    finalizeConnection(remoteInfo.lastConnected == null)
                }
            }
        } catch (e: Exception) {
            if (_connectionState.value.isForcedDisconnect) {
                stop(true)
            } else {
                stop(false)
                Log.e(TAG, "Error in connecting", e)
                _connectionState.value = ConnectionState.Error(getString(R.string.connection_error))
            }
        }
    }

    private suspend fun initializeConnection(ipAddress: String, port: Int): Boolean {
        try {
            socket = socketFactory.tcpClientSocket(SocketType.DEFAULT, ipAddress, port)
            if (socket != null) {
                try {
                    writeChannel = socket?.openWriteChannel()
                    readChannel = socket?.openReadChannel()
                    connectedIpAddress = ipAddress
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open channels", e)
                    withContext(Dispatchers.IO) {
                        socket?.close()
                        socket = null
                    }
                    return false
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            withContext(Dispatchers.IO) {
                socket?.close()
                socket = null
            }
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendDeviceInfo(remoteInfo: RemoteDevice) {
        val localDevice = appRepository.getLocalDevice()
        // Generate nonce and proof
        val nonce = ECDHHelper.generateNonce()
        val sharedSecret = ECDHHelper.deriveSharedSecret(
            localDevice.privateKey,
            remoteInfo.publicKey
        )
        val proof = ECDHHelper.generateProof(sharedSecret, nonce)

        val wallpaperBase64 = try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            wallpaperManager.drawable?.let { drawable ->
                drawableToBase64Compressed(drawable)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to access wallpaper", e)
            null
        }

        val localPhoneNumbers : List<PhoneNumber> = try {
            TelephonyHelper.getAllPhoneNumbers(this).map { it.toDto() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local phone numbers", e)
            emptyList()
        }

        sendMessage(DeviceInfo(
            deviceId = localDevice.deviceId,
            deviceName = localDevice.deviceName,
            model = localDevice.model,
            publicKey = localDevice.publicKey,
            avatar = wallpaperBase64,
            nonce = nonce,
            proof = proof,
            phoneNumbers = localPhoneNumbers
        ))
    }

    private suspend fun sendInstalledApps() {
        getInstalledApps(packageManager).forEach { app ->
            sendMessage(app)
            delay(10)
        }
    }


    private suspend fun sendContacts() {
        ContactsHelper().getAllContacts(this).forEach { contact ->
            sendMessage(contact)
            delay(10)
        }
    }

    private suspend fun finalizeConnection(isNewDevice: Boolean) {
        networkDiscovery.register(NetworkAction.SAVE_NETWORK)
        sendDeviceStatus()
        notificationHandler.sendActiveNotifications()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && preferencesRepository.readRemoteStorageSettings().first()
            && checkStoragePermission(this)
        ) {
            sftpServer.initialize()
            sftpServer.start()?.let { sendMessage(it) }
        }
        if (preferencesRepository.readMessageSyncSettings().first() && smsPermissionGranted(this)) {
            smsHandler.start()
        }
        if (isNewDevice) {
            sendInstalledApps()
            sendContacts()
        }
        networkDiscovery.unregister()
    }

    fun stop(forcedStop: Boolean) {
        if (forcedStop) {
            networkDiscovery.unregister()
            networkDiscovery.stopPairedDeviceListener()
            _connectionState.value = ConnectionState.Disconnected(true)
        } else {
            scope.launch {
                if (preferencesRepository.readDiscoveryMode() != DiscoveryMode.DISABLED)
                    networkDiscovery.register(NetworkAction.START_DEVICE_DISCOVERY)
            }
            _connectionState.value = ConnectionState.Disconnected()
        }
        deviceStatus = null
        sftpServer.stop()
        mediaHandler.release(true)
        actionHandler.clearActions()
        smsHandler.stop()
        CoroutineScope(Dispatchers.IO).launch {
            writeChannel?.flushAndClose()
            socket?.close()
        }
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
                val currentDeviceStatus = getDeviceStatus()
                // Send status if any field has changed
                if (currentDeviceStatus != deviceStatus) {
                    deviceStatus = currentDeviceStatus
                    sendMessage(currentDeviceStatus)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send device status", e)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        registerReceiver(interruptionFilterReceiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))
        registerReceiver(interruptionFilterReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        registerReceiver(interruptionFilterReceiver, IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED))

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

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

    private val interruptionFilterReceiver = object : BroadcastReceiver() {
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

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetooth = bluetoothManager.adapter.isEnabled

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Get DND state
        val isDndEnabled = notificationManager.currentInterruptionFilter != 
            NotificationManager.INTERRUPTION_FILTER_ALL

        val ringerMode = audioManager.ringerMode
        // RINGER_MODE_SILENT = 0
        // RINGER_MODE_VIBRATE = 1
        // RINGER_MODE_NORMAL = 2

        return DeviceStatus(
            batteryStatus = batteryStatus,
            bluetoothStatus = bluetooth,
            chargingStatus = isCharging,
            isDndEnabled = isDndEnabled,
            ringerMode = ringerMode
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(interruptionFilterReceiver)
        sftpServer.stop()
        mediaHandler.release(true)
        smsHandler.stop()
        writeChannel?.close()
        socket?.close()
    }
}