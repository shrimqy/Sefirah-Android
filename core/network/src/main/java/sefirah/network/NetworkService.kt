package sefirah.network

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.smsPermissionGranted
import sefirah.communication.call.CallStateReceiver
import sefirah.communication.utils.ContactsHelper
import sefirah.communication.utils.TelephonyHelper
import sefirah.database.AppRepository
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.interfaces.SocketFactory
import sefirah.projection.media.RemotePlaybackHandler
import sefirah.projection.media.PlaybackService
import sefirah.communication.sms.SmsHandler
import sefirah.notification.NotificationService
import sefirah.clipboard.ClipboardHandler
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.AudioStreamState
import sefirah.domain.model.Authentication
import sefirah.domain.model.BatteryState
import sefirah.domain.model.ClipboardInfo
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.ConnectionAck
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceConnection
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.Disconnect
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.DndState
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.RingerModeState
import sefirah.domain.model.SocketMessage
import sefirah.domain.util.MessageSerializer
import sefirah.network.extensions.ActionHandler
import sefirah.network.extensions.cancelPairingVerificationNotification
import sefirah.network.extensions.handleMessage
import sefirah.network.extensions.setNotification
import sefirah.network.transfer.FileTransferService
import sefirah.network.transfer.SftpServer
import sefirah.network.util.SslHelper
import sefirah.network.util.getInstalledApps
import sefirah.common.util.drawableToBase64Compressed
import sefirah.domain.model.BaseRemoteDevice
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import kotlin.properties.Delegates

@AndroidEntryPoint
class NetworkService : Service() {
    @Inject lateinit var socketFactory: SocketFactory

    @Inject lateinit var appRepository: AppRepository

    @Inject lateinit var notificationHandler: NotificationService

    @Inject lateinit var notificationCenter: NotificationCenter

    @Inject lateinit var clipboardHandler: ClipboardHandler

    @Inject lateinit var networkDiscovery: NetworkDiscovery

    @Inject lateinit var remotePlaybackHandler: RemotePlaybackHandler

    @Inject lateinit var playbackService: PlaybackService

    @Inject lateinit var sftpServer: SftpServer

    @Inject lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var smsHandler: SmsHandler

    @Inject lateinit var actionHandler: ActionHandler

    @Inject lateinit var deviceManager: DeviceManager

    @Inject lateinit var fileTransferService: FileTransferService

    @Inject lateinit var callStateReceiver: CallStateReceiver

    val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    fun emitPendingApproval(device: PendingDeviceApproval) {
        deviceManager.setPendingApproval(device)
    }

    fun clearPendingApproval(deviceId: String) {
        deviceManager.clearPendingApproval(deviceId)
    }

    inner class LocalBinder : Binder() {
        fun getService(): NetworkService = this@NetworkService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    lateinit var notificationBuilder: NotificationCompat.Builder

    private var lastBatteryStatus: BatteryState? = null

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

                scope.launch {
                    if (connectionDetails != null) {
                        // Check if device is already paired - if so, use connectPaired, otherwise connectTo
                        val pairedDevice = deviceManager.getPairedDevice(connectionDetails.deviceId)
                        if (pairedDevice != null) {
                            connectPaired(pairedDevice)
                        } else {
                            connectTo(connectionDetails)
                        }
                    } else {
                        val lastConnectedDevice = deviceManager.pairedDevices.value
                            .maxByOrNull { it.lastConnected ?: 0L }
                        
                        lastConnectedDevice?.let { device ->
                            connectPaired(device)
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
                    val deviceId = intent.getStringExtra(DEVICE_ID_EXTRA)
                    if (deviceId != null) {
                        disconnect(deviceId)
                    } else {
                        val lastConnectedDevice = deviceManager.pairedDevices.value
                            .maxByOrNull { it.lastConnected ?: 0L }

                        lastConnectedDevice?.let { device ->
                            disconnect(device.deviceId)
                        }
                    }
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
            Actions.SEND_CLIPBOARD.name -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    sendClipboardMessage(ClipboardInfo("text/plain", text))
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
                message as? Authentication
            } ?: run {
                Log.e(TAG, "Timeout waiting for Authentication message from incoming connection")
                sslSocket.close()
                return
            }
            val address = (sslSocket.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress ?: ""

            Log.d(TAG, "Received Authentication from ${authMessage.deviceId}: ${authMessage.deviceName}")

            val certificate = SslHelper.verifySessionCertificate(sslSocket.session, authMessage.publicKey)
            if (certificate == null) {
                Log.w(TAG, "No client certificate or public key mismatch with TLS peer; rejecting connection")
                sslSocket.close()
                return
            }

            when (val device = deviceManager.getDevice(authMessage.deviceId)) {
                is PairedDevice -> authenticatePairedDevice(
                    sslSocket,
                    readChannel,
                    writeChannel,
                    authMessage,
                    device,
                    address,
                    certificate
                )
                else -> authenticateNewDevice(
                    sslSocket,
                    readChannel,
                    writeChannel,
                    authMessage,
                    address,
                    certificate,
                    device
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
        authMessage: Authentication,
        device: PairedDevice,
        address: String,
        certificate: X509Certificate
    ) {

        if (!certificate.encoded.contentEquals(device.certificate)) {
            Log.w(TAG, "Certificate does not match pinned cert for paired device ${authMessage.deviceId}")
            sslSocket.close()
            return
        }

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
            connectionState = ConnectionState.Connected,
        )

        sendAuthMessage(writeChannel)

        deviceManager.addOrUpdatePairedDevice(updatedDevice)
        sendMessage(device.deviceId, ConnectionAck)
        finalizeConnection(updatedDevice, false)
    }

    private suspend fun authenticateNewDevice(
        sslSocket: SSLSocket,
        readChannel: ByteReadChannel,
        writeChannel: ByteWriteChannel,
        authMessage: Authentication,
        address: String,
        certificate: X509Certificate,
        device: BaseRemoteDevice?
    ) {
        if (device != null) {
            deviceManager.removeDiscoveredDevice(device.deviceId)
        }

        sendAuthMessage(writeChannel)

        val verificationCode = SslHelper.getVerificationCode(certificate, SslHelper.certificate)

        val newDevice = DiscoveredDevice(
            authMessage.deviceId,
            authMessage.deviceName,
            address,
            sslSocket.port,
            listOfNotNull(address),
            certificate,
            verificationCode,
        )

        val connection = DeviceConnection(newDevice.deviceId, sslSocket, readChannel, writeChannel)
        setConnection(newDevice.deviceId, connection)
        deviceManager.addOrUpdateDiscoveredDevice(newDevice)
    }

    suspend fun connectPaired(device: PairedDevice) {
        deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Connecting(device.deviceId)))

        val port = device.port ?: PORT_RANGE.first

        try {
            val sslSocket = run {
                for (ip in device.getAddressesToTry()) {
                    socketFactory.tcpClientSocket(ip, port, device.certificate)?.let { return@run it }
                }
                null
            } ?: run {
                Log.e(TAG, "All connection attempts failed")
                deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected()))
                return
            }

            val readChannel = sslSocket.inputStream.toByteReadChannel()
            val writeChannel = sslSocket.outputStream.asByteWriteChannel()

            sendAuthMessage(writeChannel)

            val connection = DeviceConnection(device.deviceId, sslSocket, readChannel, writeChannel)
            setConnection(device.deviceId, connection)

            val remoteAddress = (sslSocket.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress ?: ""
            val updatedDevice = device.copy(
                lastConnected = System.currentTimeMillis(),
                connectionState = ConnectionState.Connected,
                port = port,
                address = remoteAddress
            )
            deviceManager.addOrUpdatePairedDevice(updatedDevice)

            Log.d(TAG, "Device ${updatedDevice.deviceId} connected")
            sendMessage(device.deviceId, ConnectionAck)
            finalizeConnection(updatedDevice, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection", e)
            deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected()))
        }
    }


    suspend fun connectTo(connectionDetails: ConnectionDetails) {
        removeConnection(connectionDetails.deviceId)
        deviceManager.removeDiscoveredDevice(connectionDetails.deviceId)

        try {
            val sslSocket = run {
                connectionDetails.prefAddress?.let { prefAddress ->
                    socketFactory.tcpClientSocket(prefAddress, connectionDetails.port)?.let { return@run it }
                }
                for (ip in connectionDetails.addresses) {
                    socketFactory.tcpClientSocket(ip, connectionDetails.port)?.let { return@run it }
                }
                null
            } ?: run {
                Log.e(TAG, "All connection attempts failed")
                return
            }

            val readChannel = sslSocket.inputStream.toByteReadChannel()
            val writeChannel = sslSocket.outputStream.asByteWriteChannel()

            sendAuthMessage(writeChannel)

            val authResponse = try {
                withTimeoutOrNull(3000) {
                    readChannel.readUTF8Line()?.let {
                        MessageSerializer.deserialize(it) as? Authentication
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while reading Authentication message: ${e.message}", e)
                null
            } ?: run {
                Log.e(TAG, "Timeout or null response waiting for Authentication message")
                readChannel.cancel()
                sslSocket.close()
                return
            }

            val certificate = SslHelper.verifySessionCertificate(sslSocket.session, authResponse.publicKey)
            if (certificate == null) {
                Log.e(TAG, "No server certificate or public key mismatch with TLS peer; rejecting")
                readChannel.cancel()
                sslSocket.close()
                return
            }

            val address = (sslSocket.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress ?: ""

            val verificationCode = SslHelper.getVerificationCode(certificate, SslHelper.certificate)

            val connectedDevice = DiscoveredDevice(
                authResponse.deviceId,
                authResponse.deviceName,
                address,
                connectionDetails.port,
                connectionDetails.addresses,
                certificate,
                verificationCode
            )

            val connection = DeviceConnection(connectedDevice.deviceId, sslSocket, readChannel, writeChannel)
            setConnection(connectedDevice.deviceId, connection)

            deviceManager.addOrUpdateDiscoveredDevice(connectedDevice)
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectTo", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendDeviceInfo(device: PairedDevice) {
        try {
            val wallpaper = try {
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
                deviceManager.localDevice.deviceName,
                wallpaper,
                localPhoneNumbers
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
                sendMessage(it.deviceId, Disconnect)
            }
            disconnectDevice(it, true)
        }
    }

    suspend fun disconnectDevice(device: PairedDevice, forcedDisconnect: Boolean = false) {
        deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected(forcedDisconnect)))
        removeConnection(device.deviceId)

        remotePlaybackHandler.clearDeviceData(device.deviceId)
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

    fun sendClipboardMessage(message: ClipboardInfo) {
        scope.launch {
            deviceManager.pairedDevices.value.forEach { device ->
                if (device.connectionState.isConnected) {
                    if (preferencesRepository.readClipboardSyncSettingsForDevice(device.deviceId).first()) {
                        sendMessage(device.deviceId, message)
                    }
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
        sendDeviceStatus(device.deviceId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && preferencesRepository.readRemoteStorageSettingsForDevice(device.deviceId).first()
            && checkStoragePermission(this)
        ) {
            sftpServer.initialize()
            sftpServer.start()?.let { sendMessage(device.deviceId, it) }
        }

        if (preferencesRepository.readNotificationSyncSettingsForDevice(device.deviceId).first()) {
            notificationHandler.sendActiveNotifications(device.deviceId)
        }

        if (preferencesRepository.readMessageSyncSettingsForDevice(device.deviceId).first()
            && smsPermissionGranted(this)
        ) {
            smsHandler.sendAllConversations(device.deviceId)
        }

        playbackService.sendActiveSessions(device.deviceId)

        networkDiscovery.saveCurrentNetworkAsTrusted()

        if (isNewDevice) {
            sendInstalledApps(device)
            sendContacts(device)
        }
        callStateReceiver.register(this)
    }

    private suspend fun sendAuthMessage(writeChannel: ByteWriteChannel) {
        val localDevice = deviceManager.localDevice
        val authenticationMessage = Authentication(
            localDevice.deviceId,
            localDevice.deviceName,
            SslHelper.publicKeyString,
            localDevice.model
        )
        val jsonMessage = MessageSerializer.serialize(authenticationMessage)
        writeChannel.writeStringUtf8("$jsonMessage\n")
        writeChannel.flush()
    }

    private fun broadcastBatteryStatus() {
        scope.launch {
            val batteryStatus = getBatteryStatus()
            if (batteryStatus != lastBatteryStatus) {
                broadcastMessage(batteryStatus)
                lastBatteryStatus = batteryStatus
            }
        }
    }

    private fun broadcastRingerMode() {
       broadcastMessage(RingerModeState(audioManager.ringerMode))
    }

    private fun broadcastDndStatus() {
        val dndStatus = getDndStatus()
        broadcastMessage(dndStatus)
    }

    private fun sendDeviceStatus(deviceId: String) {
        val batteryStatus = getBatteryStatus()
        val ringerMode = RingerModeState(audioManager.ringerMode)
        val dndStatus = getDndStatus()

        sendMessage(deviceId, batteryStatus)
        sendMessage(deviceId, ringerMode)
        sendMessage(deviceId, dndStatus)

        getAudioLevels().forEach { audioLevel ->
            sendMessage(deviceId, audioLevel)
        }

        lastBatteryStatus = batteryStatus
    }

    private fun sendInstalledApps(device: PairedDevice) {
        getInstalledApps(packageManager).forEach {
            sendMessage(device.deviceId, it)
        }
    }

    private fun sendContacts(device: PairedDevice) {
        try {
            if (smsPermissionGranted(this)) return

            ContactsHelper().getAllContacts(this).forEach { contact ->
                sendMessage(device.deviceId, contact)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending contacts", e)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            broadcastBatteryStatus()
        }
    }

    private val interruptionFilterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED -> broadcastDndStatus()
                AudioManager.RINGER_MODE_CHANGED_ACTION -> broadcastRingerMode()
            }
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

    private fun getBatteryStatus(): BatteryState {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        return BatteryState(batteryLevel, isCharging)
    }

    private fun getDndStatus(): DndState {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val isDndEnabled = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        return DndState(isDndEnabled)
    }

    private fun getAudioLevels(): List<AudioStreamState> {
        val streamTypes = listOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
        )
        return streamTypes.map { streamType ->
            val level = 100 * audioManager.getStreamVolume(streamType) / audioManager.getStreamMaxVolume(streamType)
            AudioStreamState(streamType, level)
        }
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
            connectionState = ConnectionState.Connected,
            port = discoveredDevice.port,
            address = discoveredDevice.address,
            certificate = discoveredDevice.certificate.encoded
        )

        // Send pairing message before removing DiscoveredDevice
        sendMessage(deviceId, PairMessage(true))

        deviceManager.removeDiscoveredDevice(deviceId)
        deviceManager.addOrUpdatePairedDevice(pairedDevice)
        Log.d(TAG, "Approved $deviceId")

        sendMessage(deviceId, ConnectionAck)
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
        callStateReceiver.unregister(this)
        sftpServer.stop()
        remotePlaybackHandler.release()
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
            SEND_CLIPBOARD,
            SEND_FILES
        }

        val PORT_RANGE = 5150..5169
        const val TAG = "NetworkService"
        const val DEVICE_ID_EXTRA = "device_id"
        const val EXTRA_CONNECTION_DETAILS = "extra_connection_details"
        const val EXTRA_TRANSFER_ID = "extra_transfer_id"
    }
}
