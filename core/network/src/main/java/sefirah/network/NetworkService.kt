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
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.address
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.streams.asByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.clipboard.ClipboardHandler
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.smsPermissionGranted
import sefirah.communication.sms.SmsHandler
import sefirah.communication.utils.ContactsHelper
import sefirah.communication.utils.TelephonyHelper
import sefirah.database.AppRepository
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.AuthenticationMessage
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.CommandMessage
import sefirah.domain.model.CommandType
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceConnection
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DeviceStatus
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.SocketMessage
import sefirah.domain.repository.DeviceManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.domain.util.MessageSerializer
import sefirah.network.extensions.ActionHandler
import sefirah.network.extensions.cancelPairingVerificationNotification
import sefirah.network.extensions.handleMessage
import sefirah.network.extensions.setNotification
import sefirah.network.transfer.FileTransferService
import sefirah.network.transfer.SftpServer
import sefirah.network.util.ECDHHelper
import sefirah.network.util.TrustManager
import sefirah.network.util.getInstalledApps
import sefirah.notification.NotificationHandler
import sefirah.presentation.util.drawableToBase64Compressed
import sefirah.projection.media.MediaHandler
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import kotlin.properties.Delegates

@AndroidEntryPoint
class NetworkService : Service() {
    @Inject
    lateinit var socketFactory: SocketFactory

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var notificationCenter: NotificationCenter

    @Inject
    lateinit var clipboardHandler: ClipboardHandler

    @Inject
    lateinit var networkDiscovery: NetworkDiscovery

    @Inject
    lateinit var mediaHandler: MediaHandler

    @Inject
    lateinit var sftpServer: SftpServer

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var smsHandler: SmsHandler

    @Inject
    lateinit var actionHandler: ActionHandler

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var customTrustManager: TrustManager

    @Inject
    lateinit var fileTransferService: FileTransferService

    val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    private val _pendingDeviceApproval = MutableStateFlow<PendingDeviceApproval?>(null)
    val pendingDeviceApproval: StateFlow<PendingDeviceApproval?> = _pendingDeviceApproval.asStateFlow()

    fun emitPendingApproval(device: PendingDeviceApproval) {
        _pendingDeviceApproval.value = device
    }

    fun clearPendingApproval(deviceId: String) {
        if (_pendingDeviceApproval.value?.deviceId == deviceId) {
            _pendingDeviceApproval.value = null
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): NetworkService = this@NetworkService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    lateinit var notificationBuilder: NotificationCompat.Builder

    private var deviceStatus: DeviceStatus? = null

    private var tcpServerSocket: javax.net.ssl.SSLServerSocket? = null
    private var serverAcceptJob: Job? = null

    private val connections = mutableMapOf<String, DeviceConnection>()

    private var tcpServerPort by Delegates.notNull<Int>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.CONNECT.name -> {
                val connectionDetails = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CONNECTION_DETAILS, ConnectionDetails::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CONNECTION_DETAILS)
                }
                connectionDetails?.let {
                    scope.launch {
                        // Check if device is already paired - if so, use connectPaired, otherwise connectTo
                        val pairedDevice = deviceManager.getPairedDevice(it.deviceId)
                        if (pairedDevice != null) {
                            connectPaired(pairedDevice)
                        } else {
                            connectTo(it)
                        }
                    }
                }
            }

            Actions.APPROVE_DEVICE.name -> {
                scope.launch {
                    intent.getStringExtra(DEVICE_ID_EXTRA)?.let {
                        approveDeviceConnection(it)
                    }
                }
            }

            Actions.REJECT_DEVICE.name -> {
                scope.launch {
                    intent.getStringExtra(DEVICE_ID_EXTRA)?.let {
                        rejectDeviceConnection(it)
                    }
                }
            }

            Actions.DISCONNECT.name -> {
                scope.launch {
                    val deviceId = intent.getStringExtra(DEVICE_ID_EXTRA) ?: return@launch
                    disconnect(deviceId)
                }
            }

            Actions.CANCEL_TRANSFER.name -> {
                val transferId = intent.getStringExtra(EXTRA_TRANSFER_ID) ?: return START_STICKY
                fileTransferService.cancelTransfer(transferId)
            }

            Actions.SEND_FILES.name -> {
                val deviceId = intent.getStringExtra(DEVICE_ID_EXTRA) ?: return START_STICKY
                val clipData = intent.clipData ?: return START_STICKY
                val uris = (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
                if (uris.isNotEmpty()) {
                    fileTransferService.sendFiles(deviceId, uris)
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registerReceiver(interruptionFilterReceiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))
        registerReceiver(interruptionFilterReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        registerReceiver(interruptionFilterReceiver, IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED))
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        setNotification(null, null, AppNotifications.DEVICE_CONNECTION_ID)

        scope.launch {
            tcpServerPort = startTcpServer()
            networkDiscovery.initialize(tcpServerPort)
        }

        scope.launch {
            deviceManager.pairedDevices.collect { pairedDevices ->
                // Get connected devices
                val connectedDevices = pairedDevices.filter { it.connectionState.isConnected }

                if (connectedDevices.isNotEmpty()) {
                    val deviceNames = connectedDevices.joinToString(", ") { it.deviceName }
                    // Only pass deviceId for disconnect action when single device connected
                    val deviceId = if (connectedDevices.size == 1) connectedDevices.first().deviceId else null
                    setNotification(deviceNames, deviceId, AppNotifications.DEVICE_CONNECTION_ID)
                } else {
                    setNotification(null, null, AppNotifications.DEVICE_CONNECTION_ID)
                }
            }
        }
    }

    suspend fun startTcpServer(): Int {
        try {
            tcpServerSocket = socketFactory.tcpServerSocket(PORT_RANGE)
                ?: throw IllegalStateException("Failed to create TCP server socket")
            val port = tcpServerSocket!!.localPort
            Log.d(TAG, "TCP server started successfully on port $port")

            // Start accepting connections
            serverAcceptJob = scope.launch {
                while (tcpServerSocket?.isClosed == false) {
                    try {
                        val sslSocket = withContext(Dispatchers.IO) {
                            tcpServerSocket?.accept() as? SSLSocket
                        } ?: break

                        Log.d(TAG, "Accepted incoming connection from ${sslSocket.remoteSocketAddress}")
                        handleIncomingConnection(sslSocket)
                    } catch (e: Exception) {
                        if (tcpServerSocket?.isClosed == true) {
                            Log.d(TAG, "Server socket closed, stopping acceptance loop")
                            break
                        }
                        Log.e(TAG, "Error accepting connection", e)
                    }
                }
                Log.d(TAG, "TCP server stopped")
            }
            return port
        } catch (e: Exception) {
            Log.e(TAG, "Error starting TCP server", e)
            throw e
        }
    }

    private suspend fun handleIncomingConnection(sslSocket: SSLSocket) {
        try {
            val readChannel = sslSocket.inputStream.toByteReadChannel()
            val writeChannel = sslSocket.outputStream.asByteWriteChannel()

            val authMessage = readChannel.readUTF8Line()?.let {
                val message = MessageSerializer.deserialize(it)
                message as? AuthenticationMessage
            } ?: run {
                Log.e(TAG, "Timeout waiting for Authentication message from incoming connection")
                sslSocket.close()
                return
            }
            val address = sslSocket.remoteSocketAddress.address

            Log.d(TAG, "Received Authentication from ${authMessage.deviceId}: ${authMessage.deviceName}")

            val existingDevice = deviceManager.getPairedDevice(authMessage.deviceId)
            if (existingDevice != null) {
                authenticatePairedDevice(
                    sslSocket,
                    readChannel,
                    writeChannel,
                    authMessage,
                    existingDevice,
                    address,
                )
            } else {
                authenticateNewDevice(
                    sslSocket,
                    readChannel,
                    writeChannel,
                    authMessage,
                    address
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming connection", e)
            sslSocket.close()
        }
    }

    private suspend fun authenticatePairedDevice(
        sslSocket: SSLSocket,
        readChannel: ByteReadChannel,
        writeChannel: ByteWriteChannel,
        authMessage: AuthenticationMessage,
        device: PairedDevice,
        address: String
    ) {
        // Verify authentication
        if (!verifyAuthentication(device.publicKey, authMessage)) {
            Log.e(TAG, "Authentication failed for paired device ${authMessage.deviceId}")
            sslSocket.close()
            return
        }

        Log.d(TAG, "Authentication successful for paired device ${authMessage.deviceId}")

        // Create/update DeviceConnection
        val connection = DeviceConnection(
            deviceId = device.deviceId,
            sslSocket = sslSocket,
            readChannel = readChannel,
            writeChannel = writeChannel
        )
        setConnection(device.deviceId, connection)

        val updatedDevice = device.copy(
            deviceName = authMessage.deviceName,
            lastConnected = System.currentTimeMillis(),
            addresses = if (device.addresses.none { it.address == address }) {
                device.addresses + AddressEntry(address)
            } else {
                device.addresses
            },
            address = address,
            connectionState = ConnectionState.Connected
        )

        sendAuthMessage(device.publicKey, writeChannel)

        deviceManager.addOrUpdatePairedDevice(updatedDevice)
        finalizeConnection(updatedDevice, false)
    }

    private suspend fun authenticateNewDevice(
        sslSocket: SSLSocket,
        readChannel: ByteReadChannel,
        writeChannel: ByteWriteChannel,
        authMessage: AuthenticationMessage,
        address: String
    ) {
        val existingDevice = deviceManager.getDiscoveredDevice(authMessage.deviceId)
        if (existingDevice != null) {
            deviceManager.removeDiscoveredDevice(existingDevice.deviceId)
        }

        if (!verifyAuthentication(authMessage.publicKey, authMessage)) {
            Log.e(TAG, "Authentication failed for new device ${authMessage.deviceId}")
            sslSocket.close()
            return
        }
        sendAuthMessage(authMessage.publicKey, writeChannel)

        val localDevice = deviceManager.localDevice
        val sharedSecret = ECDHHelper.deriveSharedSecret(
            localDevice.privateKey,
            authMessage.publicKey
        )
        val verificationCode = generateVerificationCode(sharedSecret)

        val newDevice = DiscoveredDevice(
            deviceId = authMessage.deviceId,
            deviceName = authMessage.deviceName,
            publicKey = authMessage.publicKey,
            addresses = listOfNotNull(address),
            address = address,
            verificationCode = verificationCode
        )

        val connection = DeviceConnection(
            deviceId = newDevice.deviceId,
            sslSocket = sslSocket,
            readChannel = readChannel,
            writeChannel = writeChannel
        )
        setConnection(newDevice.deviceId, connection)

        deviceManager.addOrUpdateDiscoveredDevice(newDevice)
    }

    suspend fun connectPaired(device: PairedDevice) {
        deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Connecting(device.deviceId)))

        val port = device.port ?: PORT_RANGE.first

        try {
            val socket = run {
                for (ip in device.getAddressesToTry()) {
                    try {
                        socketFactory.tcpClientSocket(ip, port)?.let { return@run it }
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to connect to $ip:$port", e)
                    }
                }
                null
            } ?: run {
                Log.e(TAG, "All connection attempts failed")
                deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected()))
                return
            }

            val writeChannel = socket.openWriteChannel()
            val readChannel = socket.openReadChannel()

            sendAuthMessage(device.publicKey, writeChannel)

            val authResponse = try {
                withTimeoutOrNull(3000) {
                    readChannel.readUTF8Line()?.let {
                        MessageSerializer.deserialize(it) as? AuthenticationMessage
                    }
                }
            } catch (_: Exception) {
                null
            } ?: run {
                Log.e(TAG, "Timeout or null response waiting for authentication message")
                socket.close()
                deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected()))
                return
            }

            if (!verifyAuthentication(device.publicKey, authResponse)) {
                Log.e(TAG, "Authentication failed ${authResponse.deviceId}")
                socket.close()
                deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected()))
                return
            }

            val connection = DeviceConnection(
                deviceId = device.deviceId,
                socket = socket,
                readChannel = readChannel,
                writeChannel = writeChannel
            )
            setConnection(device.deviceId, connection)

            val updatedDevice = device.copy(
                lastConnected = System.currentTimeMillis(),
                connectionState = ConnectionState.Connected,
                port = port,
                address = socket.remoteAddress.toJavaAddress().address
            )
            deviceManager.addOrUpdatePairedDevice(updatedDevice)

            Log.d(TAG, "Device ${updatedDevice.deviceId} connected")
            finalizeConnection(updatedDevice, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection", e)
            deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected()))
        }
    }


    suspend fun connectTo(connectionDetails: ConnectionDetails) {
        removeConnection(connectionDetails.deviceId)
        deviceManager.removeDiscoveredDevice(connectionDetails.deviceId)

        val addresses = connectionDetails.addresses
        val port = connectionDetails.port
        val remotePublicKey = connectionDetails.publicKey

        try {
            val socket = run {
                for (ip in addresses) {
                    try {
                        socketFactory.tcpClientSocket(ip, port)?.let { return@run it }
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to connect to $ip:$port", e)
                    }
                }
                null
            } ?: run {
                Log.e(TAG, "All connection attempts failed")
                return
            }

            val writeChannel = socket.openWriteChannel()
            val readChannel = socket.openReadChannel()

            sendAuthMessage(remotePublicKey, writeChannel)

            val authResponse = try {
                withTimeoutOrNull(3000) {
                    readChannel.readUTF8Line()?.let {
                        MessageSerializer.deserialize(it) as? AuthenticationMessage
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while reading Authentication message: ${e.message}", e)
                null
            } ?: run {
                Log.e(TAG, "Timeout or null response waiting for Authentication message")
                readChannel.cancel()
                socket.close()
                return
            }

            val sharedSecret = ECDHHelper.deriveSharedSecret(deviceManager.localDevice.privateKey, remotePublicKey)

            if (!ECDHHelper.verifyProof(sharedSecret, authResponse.nonce, authResponse.proof)) {
                Log.e(TAG, "Authentication failed")
                readChannel.cancel()
                socket.close()
                return
            }

            val verificationCode = generateVerificationCode(sharedSecret)

            val connectedDevice = DiscoveredDevice(
                deviceId = authResponse.deviceId,
                deviceName = authResponse.deviceName,
                publicKey = authResponse.publicKey,
                addresses = addresses,
                verificationCode = verificationCode,
                port = port
            )

            // Create and store DeviceConnection
            val connection = DeviceConnection(
                deviceId = connectedDevice.deviceId,
                socket = socket,
                readChannel = readChannel,
                writeChannel = writeChannel
            )
            setConnection(connectedDevice.deviceId, connection)

            deviceManager.addOrUpdateDiscoveredDevice(connectedDevice)
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectTo", e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendDeviceInfo(device: PairedDevice) {
        try {
            val wallpaperBase64 = try {
                val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                wallpaperManager.drawable?.let { drawable ->
                    drawableToBase64Compressed(drawable)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Unable to access wallpaper", e)
                null
            }

            val localPhoneNumbers = try {
                TelephonyHelper.getAllPhoneNumbers(this).map { it.toDto() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get local phone numbers", e)
                emptyList()
            }

            val deviceInfo = DeviceInfo(
                deviceName = deviceManager.localDevice.deviceName,
                avatar = wallpaperBase64,
                phoneNumbers = localPhoneNumbers
            )
            sendMessage(device.deviceId, deviceInfo)
            Log.d(TAG, "DeviceInfo sent to ${device.deviceId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send DeviceInfo to ${device.deviceId}", e)
        }
    }

    suspend fun disconnect(deviceId: String) {
        deviceManager.getPairedDevice(deviceId)?.let {
            if (it.connectionState.isConnected) {
                sendMessage(it.deviceId, CommandMessage(CommandType.Disconnect))
            }
            disconnectDevice(it, true)
        }
    }



    suspend fun disconnectDevice(device: PairedDevice, forcedDisconnect: Boolean = false) {
        deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected(forcedDisconnect)))
        removeConnection(device.deviceId)

        mediaHandler.clearDeviceData(device.deviceId)
        actionHandler.clearDeviceActions(device.deviceId)
    }

    private suspend fun disconnectDevice(device: DiscoveredDevice) {
        deviceManager.removeDiscoveredDevice(device.deviceId)
        removeConnection(device.deviceId)
    }

    fun broadcastMessage(message: SocketMessage) {
        deviceManager.pairedDevices.value.forEach { device ->
            if (device.connectionState.isConnected) {
                sendMessage(device.deviceId, message)
            }
        }
    }

    suspend fun sendClipboardMessage(message: ClipboardMessage) {
        deviceManager.pairedDevices.value.forEach { device ->
            if (device.connectionState.isConnected) {
                if (preferencesRepository.readClipboardSyncSettingsForDevice(device.deviceId).first()) {
                    sendMessage(device.deviceId, message)
                }
            }
        }
    }

    /**
     * Starts listening for messages from a device connection.
     */
    private fun startListeningForDevice(connection: DeviceConnection) {
        connection.startListening(
            getDevice = { deviceManager.getDevice(it) },
            onMessage = { device, message -> scope.launch { handleMessage(device, message) } },
            onClose = { id ->
                scope.launch {
                    when (val device = deviceManager.getDevice(id)) {
                        is PairedDevice -> connections[id]?.let { disconnectDevice(device) }
                        is DiscoveredDevice -> disconnectDevice(device)
                    }
                }
            }
        )
    }

    suspend fun finalizeConnection(device: PairedDevice, isNewDevice: Boolean) {
        sendDeviceInfo(device)
        sendMessage(device.deviceId, getDeviceStatus())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && preferencesRepository.readRemoteStorageSettingsForDevice(device.deviceId).first()
            && checkStoragePermission(this)
        ) {
            sftpServer.initialize()
            sftpServer.start()?.let { sendMessage(device.deviceId, it) }
        }

        if (preferencesRepository.readMessageSyncSettingsForDevice(device.deviceId).first()
            && smsPermissionGranted(this)
        ) {
            smsHandler.sendAllConversations(device.deviceId)
        }

        if (preferencesRepository.readNotificationSyncSettingsForDevice(device.deviceId).first()) {
            notificationHandler.sendActiveNotifications(device.deviceId)
        }

        networkDiscovery.saveCurrentNetworkAsTrusted()

        if (isNewDevice) {
            sendInstalledApps(device)
            sendContacts(device)
        }
    }

    private fun verifyAuthentication(
        remotePublicKey: String,
        authMessage: AuthenticationMessage
    ): Boolean {
        val sharedSecret = ECDHHelper.deriveSharedSecret(deviceManager.localDevice.privateKey, remotePublicKey)
        return ECDHHelper.verifyProof(sharedSecret, authMessage.nonce, authMessage.proof)
    }

    private fun generateVerificationCode(sharedSecret: ByteArray): String {
        val rawKey = kotlin.math.abs(java.nio.ByteBuffer.wrap(sharedSecret).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        return rawKey.toString().takeLast(6).padStart(6, '0')
    }

    private suspend fun sendAuthMessage(
        remotePublicKey: String,
        writeChannel: ByteWriteChannel
    ) {
        val localDevice = deviceManager.localDevice

        val nonce = ECDHHelper.generateNonce()
        val sharedSecret = ECDHHelper.deriveSharedSecret(
            localDevice.privateKey,
            remotePublicKey
        )

        val proof = ECDHHelper.generateProof(sharedSecret, nonce)
        val authenticationMessage = AuthenticationMessage(
            localDevice.deviceId,
            localDevice.deviceName,
            localDevice.publicKey,
            nonce,
            proof,
            localDevice.model
        )
        val jsonMessage = MessageSerializer.serialize(authenticationMessage)
        writeChannel.writeStringUtf8("$jsonMessage\n")
        writeChannel.flush()
    }

    private fun broadcastDeviceStatus() {
        scope.launch {
            val currentDeviceStatus = getDeviceStatus()
            // Send status if any field has changed
            if (currentDeviceStatus != deviceStatus) {
                // Broadcast to all connected devices
                broadcastMessage(currentDeviceStatus)
                deviceStatus = currentDeviceStatus
            }
        }
    }

    private suspend fun sendInstalledApps(device: PairedDevice) {
        sendMessage(device.deviceId, getInstalledApps(packageManager))
    }

    private suspend fun sendContacts(device: PairedDevice) {
        try {
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_CONTACTS permission not granted, skipping contacts sync")
                return
            }
            ContactsHelper().getAllContacts(this).forEach { contact ->
                sendMessage(device.deviceId, contact)
                delay(10)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending contacts", e)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            broadcastDeviceStatus()
        }
    }

    private val interruptionFilterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            broadcastDeviceStatus()
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            networkDiscovery.broadcastDevice()
        }
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            networkDiscovery.broadcastDevice()
        }
    }

    private fun getDeviceStatus(): DeviceStatus {
        val batteryStatus: Int? =
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

        val isCharging = batteryStatus != null &&
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetooth = bluetoothManager.adapter.isEnabled

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Get DND state
        val isDndEnabled = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        // RINGER_MODE_SILENT = 0
        // RINGER_MODE_VIBRATE = 1
        // RINGER_MODE_NORMAL = 2
        val ringerMode = audioManager.ringerMode

        return DeviceStatus(
            batteryStatus = batteryStatus,
            bluetoothStatus = bluetooth,
            chargingStatus = isCharging,
            isDndEnabled = isDndEnabled,
            ringerMode = ringerMode
        )
    }

    suspend fun approveDeviceConnection(deviceId: String) {
        clearPendingApproval(deviceId)
        cancelPairingVerificationNotification(deviceId)

        val discoveredDevice = deviceManager.getDiscoveredDevice(deviceId) ?: return

        val pairedDevice = PairedDevice(
            deviceId = discoveredDevice.deviceId,
            deviceName = discoveredDevice.deviceName,
            avatar = null,
            lastConnected = System.currentTimeMillis(),
            addresses = discoveredDevice.addresses.map { AddressEntry(it) },
            publicKey = discoveredDevice.publicKey,
            connectionState = ConnectionState.Connected,
            port = discoveredDevice.port,
            address = discoveredDevice.address
        )

        // Send pairing message before removing DiscoveredDevice
        sendMessage(deviceId, PairMessage(true))

        deviceManager.removeDiscoveredDevice(deviceId)
        deviceManager.addOrUpdatePairedDevice(pairedDevice)
        Log.d(TAG, "Approved $deviceId")

        delay(100)
        finalizeConnection(pairedDevice, true)
    }

    suspend fun rejectDeviceConnection(deviceId: String) {
        val remoteDevice = deviceManager.getDiscoveredDevice(deviceId)

        if (remoteDevice != null) {
            // Send rejection message
            sendMessage(deviceId, PairMessage(pair = false))
        }

        // Clear pending approval and cancel notification
        clearPendingApproval(deviceId)
        cancelPairingVerificationNotification(deviceId)

        Log.d(TAG, "Rejected pairing request from device $deviceId")
    }

    // Connection management methods
    private fun setConnection(deviceId: String, connection: DeviceConnection) {
        connections.remove(deviceId)?.close()
        connections[deviceId] = connection
        startListeningForDevice(connection)
    }

    private fun removeConnection(deviceId: String) {
        connections.remove(deviceId)?.close()
    }

    fun sendMessage(deviceId: String, message: SocketMessage) {
        connections[deviceId]?.sendMessage(message) ?: run {
            Log.w(TAG, "Cannot send message to $deviceId: no connection found")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(interruptionFilterReceiver)
        unregisterReceiver(screenOnReceiver)
        unregisterReceiver(wifiStateReceiver)
        sftpServer.stop()
        mediaHandler.release()
        smsHandler.stop()
        serverAcceptJob?.cancel()

        try {
            tcpServerSocket?.close()
            Log.d(TAG, "TCP server closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TCP server", e)
        }
    }

    companion object {
        enum class Actions {
            CONNECT,
            APPROVE_DEVICE,
            REJECT_DEVICE,
            DISCONNECT,
            CANCEL_TRANSFER,
            SEND_FILES
        }

        val PORT_RANGE = 5150..5169
        const val TAG = "NetworkService"
        const val DEVICE_ID_EXTRA = "device_id"
        const val EXTRA_CONNECTION_DETAILS = "extra_connection_details"
        const val EXTRA_TRANSFER_ID = "extra_transfer_id"
    }
}
