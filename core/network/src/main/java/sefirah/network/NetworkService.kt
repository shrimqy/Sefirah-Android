package sefirah.network

import android.annotation.SuppressLint
import android.app.Service
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.FeatureManager
import sefirah.actions.ActionFeature
import sefirah.apps.AppListHandler
import sefirah.clipboard.ClipboardFeature
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.common.util.drawableToBase64Compressed
import sefirah.common.util.isContactsPermissionGranted
import sefirah.communication.bluetooth.BluetoothPairingHandler
import sefirah.communication.sms.SmsFeature
import sefirah.communication.utils.ContactsHelper
import sefirah.communication.utils.TelephonyHelper
import sefirah.database.AppRepository
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.interfaces.SocketFactory
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.Authentication
import sefirah.domain.model.BaseRemoteDevice
import sefirah.domain.model.ClipboardInfo
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceConnection
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.Disconnect
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.SocketMessage
import sefirah.domain.util.MessageSerializer
import sefirah.media.PlaybackFeature
import sefirah.media.RemotePlaybackFeature
import sefirah.network.extensions.cancelPairingVerificationNotification
import sefirah.network.extensions.handleMessage
import sefirah.network.extensions.startForegroundNotification
import sefirah.network.extensions.updateNotification
import sefirah.network.extensions.showPairingVerificationNotification
import sefirah.network.util.SslHelper
import sefirah.notification.NotificationFeature
import sefirah.playsound.PlaySoundFeature
import sefirah.status.DeviceControlHandler
import sefirah.status.RemoteDeviceStatusFeature
import sefirah.transfer.FileTransferService
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class NetworkService : Service() {
    @Inject lateinit var socketFactory: SocketFactory

    @Inject lateinit var appRepository: AppRepository

    @Inject lateinit var notificationFeature: NotificationFeature

    @Inject lateinit var notificationCenter: NotificationCenter

    @Inject lateinit var clipboardFeature: ClipboardFeature

    @Inject lateinit var networkDiscovery: NetworkDiscovery

    @Inject lateinit var remotePlaybackFeature: RemotePlaybackFeature

    @Inject lateinit var playbackFeature: PlaybackFeature

    @Inject lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var smsFeature: SmsFeature

    @Inject lateinit var actionFeature: ActionFeature

    @Inject lateinit var remoteDeviceStatusFeature: RemoteDeviceStatusFeature

    @Inject lateinit var deviceManager: DeviceManager

    @Inject lateinit var fileTransferService: FileTransferService

    @Inject lateinit var featureManager: FeatureManager

    @Inject lateinit var deviceControlHandler: DeviceControlHandler

    @Inject lateinit var appListHandler: AppListHandler

    @Inject lateinit var bluetoothPairingHandler: BluetoothPairingHandler

    @Inject lateinit var playSoundFeature: PlaySoundFeature

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

    private var tcpServerSocket: javax.net.ssl.SSLServerSocket? = null
    private var serverAcceptJob: Job? = null

    private val connections = mutableMapOf<String, DeviceConnection>()

    private val connectionAttempts = ConcurrentHashMap<String, Mutex>()
    private val reconnector by lazy {
        PairedDeviceReconnector(scope, deviceManager::getPairedDevice) {
            Log.d(TAG, "Automatically reconnecting paired device ${it.deviceName}")
            connectPaired(it, automatic = true)
        }
    }

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
                val transferId = intent.getStringExtra(FileTransferService.EXTRA_TRANSFER_ID)
                    ?: return START_STICKY
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
                    clipboardFeature.sendClipboard(ClipboardInfo("text/plain", text))
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        deviceControlHandler.start()
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        startForegroundNotification(AppNotifications.DEVICE_CONNECTION_ID)

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
                    updateNotification(deviceNames, deviceId, AppNotifications.DEVICE_CONNECTION_ID)
                } else {
                    updateNotification(null, null, AppNotifications.DEVICE_CONNECTION_ID)
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

                        sslSocket.keepAlive = true
                        Log.d(TAG, "Accepted incoming connection from ${sslSocket.remoteSocketAddress}")
                        launch { handleIncomingConnection(sslSocket) }
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
            withContext(Dispatchers.IO) {
                sslSocket.startHandshake()
            }

            val readChannel = sslSocket.inputStream.toByteReadChannel()
            val writeChannel = sslSocket.outputStream.asByteWriteChannel()

            val authMessage = withTimeoutOrNull(10_000.milliseconds) {
                readChannel.readUTF8Line()?.let {
                    val message = MessageSerializer.deserialize(it)
                    message as? Authentication
                }
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

        val connection = DeviceConnection(device.deviceId, sslSocket, readChannel, writeChannel)
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
            listOfNotNull(address),
            certificate,
            verificationCode
        )

        val connection = DeviceConnection(newDevice.deviceId, sslSocket, readChannel, writeChannel)
        setConnection(newDevice.deviceId, connection)
        deviceManager.addOrUpdateDiscoveredDevice(newDevice)
    }

    suspend fun connectPaired(device: PairedDevice) = connectPaired(device, automatic = false)

    private suspend fun connectPaired(device: PairedDevice, automatic: Boolean) {
        val attempt = connectionAttempts.getOrPut(device.deviceId) { Mutex() }
        if (!attempt.tryLock()) return
        var socket: SSLSocket? = null
        var started = false
        var installed = false
        try {
            val stored = deviceManager.getPairedDevice(device.deviceId) ?: return
            if (stored.connectionState.isConnectedOrConnecting ||
                (automatic && stored.connectionState.isForcedDisconnect)) return
            // Discovery can supply a newly observed address/port before it is persisted.
            val current = if (automatic) stored else device.copy(certificate = stored.certificate)
            deviceManager.addOrUpdatePairedDevice(current.copy(connectionState = ConnectionState.Connecting(current.deviceId)))
            started = true
            val port = current.port ?: PORT_RANGE.first
            val sslSocket = run {
                for (ip in current.getAddressesToTry()) {
                    currentCoroutineContext().ensureActive()
                    socketFactory.tcpClientSocket(ip, port, current.certificate)?.let { return@run it }
                }
                null
            } ?: run {
                Log.e(TAG, "All connection attempts failed")
                return
            }
            socket = sslSocket
            currentCoroutineContext().ensureActive()

            // A disconnect, unpair or incoming connection can win while TLS is in progress.
            val latest = deviceManager.getPairedDevice(current.deviceId) ?: return
            if (latest.connectionState.isForcedDisconnect || latest.connectionState.isConnected ||
                !latest.certificate.contentEquals(current.certificate)) return

            val readChannel = sslSocket.inputStream.toByteReadChannel()
            val writeChannel = sslSocket.outputStream.asByteWriteChannel()

            sendAuthMessage(writeChannel)

            val connection = DeviceConnection(device.deviceId, sslSocket, readChannel, writeChannel)
            setConnection(device.deviceId, connection)
            installed = true

            val remoteAddress = (sslSocket.remoteSocketAddress as? java.net.InetSocketAddress)?.address?.hostAddress ?: ""
            val updatedDevice = latest.copy(
                lastConnected = System.currentTimeMillis(),
                connectionState = ConnectionState.Connected,
                port = port,
                address = remoteAddress
            )
            deviceManager.addOrUpdatePairedDevice(updatedDevice)

            Log.d(TAG, "Device ${updatedDevice.deviceId} connected")
            finalizeConnection(updatedDevice, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection", e)
        } finally {
            try {
                if (started && !installed) {
                    try {
                        socket?.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing unfinished connection", e)
                    }
                    withContext(NonCancellable) {
                        val latest = deviceManager.getPairedDevice(device.deviceId)
                        if (latest?.connectionState?.isConnecting == true) {
                            deviceManager.addOrUpdatePairedDevice(latest.copy(connectionState = ConnectionState.Disconnected()))
                        }
                    }
                }
            } finally {
                attempt.unlock()
            }
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
                withTimeoutOrNull(3000.milliseconds) {
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
                connectionDetails.addresses,
                certificate,
                verificationCode,
                connectionDetails.port
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

            val deviceInfo = DeviceInfo(deviceManager.localDevice.deviceName, wallpaper, localPhoneNumbers)
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
        if (forcedDisconnect) reconnector.cancel(device.deviceId)
        Log.i(TAG, "Disconnected ${device.deviceName}")

        deviceManager.addOrUpdatePairedDevice(device.copy(connectionState = ConnectionState.Disconnected(forcedDisconnect)))
        removeConnection(device.deviceId)
        try {
            featureManager.onDisconnect(device.deviceId)
        } finally {
            if (!forcedDisconnect) reconnector.schedule(device.deviceId)
        }
    }

    private suspend fun disconnectDevice(device: DiscoveredDevice) {
        Log.i(TAG, "Disconnected ${device.deviceName}")
        deviceManager.removeDiscoveredDevice(device.deviceId)
        removeConnection(device.deviceId)
    }

    suspend fun handlePairMessage(device: DiscoveredDevice, message: PairMessage) {
        if (message.pair) {
            // Check if this is a response to our pairing request
            if (device.isPairing) {
                val pairedDevice = PairedDevice(
                    deviceId = device.deviceId,
                    deviceName = device.deviceName,
                    avatar = null,
                    lastConnected = System.currentTimeMillis(),
                    addresses = device.addresses.map { AddressEntry(it) },
                    address = device.address,
                    connectionState = ConnectionState.Connected,
                    certificate = device.certificate.encoded,
                    port = device.port,
                )

                deviceManager.removeDiscoveredDevice(device.deviceId)
                deviceManager.addOrUpdatePairedDevice(pairedDevice)
                Log.d(TAG, "Created PairedDevice ${device.deviceId} after pairing approval")

                finalizeConnection(pairedDevice, true)
            } else {
                // Remote device is requesting to pair with us
                val pendingApproval = PendingDeviceApproval(
                    device.deviceId,
                    device.deviceName,
                    device.verificationCode
                )
                emitPendingApproval(pendingApproval)
                showPairingVerificationNotification(pendingApproval)
            }
        } else {
            // Pairing rejected
            if (device.isPairing) {
                // They rejected our pairing request - update isPairing to false
                val updatedDevice = device.copy(isPairing = false)
                deviceManager.addOrUpdateDiscoveredDevice(updatedDevice)
                Log.d(TAG, "Pairing rejected by ${device.deviceId}")
            }
        }
    }

    suspend fun handleDeviceInfo(deviceInfo: DeviceInfo, device: PairedDevice) {
        val updatedDevice = device.copy(
            deviceName = deviceInfo.deviceName,
            avatar = deviceInfo.avatar
        )
        deviceManager.addOrUpdatePairedDevice(updatedDevice)
        Log.d(TAG, "DeviceInfo updated for ${device.deviceId}")
    }

    fun broadcastMessage(message: SocketMessage) {
        deviceManager.pairedDevices.value.forEach { device ->
            if (device.connectionState.isConnected) {
                sendMessage(device.deviceId, message)
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
            onClose = { closed ->
                scope.launch {
                    val id = closed.deviceId
                    if (connections[id] === closed) {
                        when (val device = deviceManager.getDevice(id)) {
                            is PairedDevice -> disconnectDevice(device)
                            is DiscoveredDevice -> disconnectDevice(device)
                        }
                    }
                }
            }
        )
    }

    suspend fun finalizeConnection(
        device: PairedDevice,
        isNewDevice: Boolean,
    ) {
        sendDeviceInfo(device)
        deviceControlHandler.sendDeviceStatus(device.deviceId)

        if (isNewDevice) {
            appListHandler.sendInstalledApps(device.deviceId)
            sendContacts(device)
        }

        networkDiscovery.saveCurrentNetworkAsTrusted()

        featureManager.onConnect(device.deviceId)
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

    private fun sendContacts(device: PairedDevice) {
        try {
            if (!isContactsPermissionGranted(this)) return

            for (contact in ContactsHelper().getAllContacts(this)) {
                if (!sendMessage(device.deviceId, contact)) {
                    Log.d(TAG, "Stopping contact sync for ${device.deviceId}; connection gone")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending contacts", e)
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
    private suspend fun setConnection(deviceId: String, connection: DeviceConnection) {
        removeConnection(deviceId)
        connections[deviceId] = connection
        startListeningForDevice(connection)
    }

    private suspend fun removeConnection(deviceId: String) {
        connections.remove(deviceId)?.close()
    }

    fun sendMessage(deviceId: String, message: SocketMessage): Boolean {
        return connections[deviceId]?.sendMessage(message) ?: false
    }

    override fun onDestroy() {
        scope.cancel()
        serverAcceptJob?.cancel()
        try {
            tcpServerSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TCP server", e)
        }

        // Clean disconnect all peers
        runBlocking {
            deviceManager.pairedDevices.value.forEach { device ->
                if (device.connectionState.isConnected) {
                    sendMessage(device.deviceId, Disconnect)
                    disconnectDevice(device, true)
                }
            }
        }

        unregisterReceiver(screenOnReceiver)
        unregisterReceiver(wifiStateReceiver)
        deviceControlHandler.stop()
        playSoundFeature.stop()
        networkDiscovery.unregister()

        remotePlaybackFeature.release()
        smsFeature.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
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
    }
}
