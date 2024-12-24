package sefirah.network

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sefirah.clipboard.ClipboardHandler
import sefirah.common.R
import sefirah.common.extensions.NotificationCenter
import sefirah.data.repository.AppRepository
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import sefirah.network.extensions.handleMessage
import sefirah.network.extensions.setNotification
import sefirah.network.util.MessageSerializer
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): NetworkService = this@NetworkService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    lateinit var notificationBuilder: NotificationCompat.Builder

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: Socket? = null
    private var writeChannel: ByteWriteChannel? = null

    lateinit var connectedDevice: RemoteDevice

    val channelName by lazy { getString(R.string.notification_device_connection) }
    val channelId = "Device Connection Status"
    val notificationId = channelId.hashCode()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.name -> {
                val remoteInfo = intent.getParcelableExtra<RemoteDevice>(REMOTE_INFO)
                if (remoteInfo != null) {
                    setNotification(false, remoteInfo.deviceName)
                    start(remoteInfo)
                }
            }
            Actions.STOP.name -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(remoteInfo: RemoteDevice) {
        scope.launch {
            try {
                val localDevice = appRepository.getLocalDevice()
                socket = socketFactory.createSocket(SocketType.DEFAULT, remoteInfo).getOrNull()
                writeChannel = socket?.openWriteChannel()
                connectedDevice = remoteInfo
                _isConnected.value = true
                startListening()
                sendMessage(DeviceInfo(
                    deviceId = localDevice.deviceId,
                    publicKey = localDevice.publicKey,
                    deviceName = localDevice.deviceName,
                    hashedSecret = remoteInfo.hashedSecret
                ))
                delay(10) // Add a delay for verification from the other side
                setNotification(true, remoteInfo.deviceName)
                notificationHandler.sendActiveNotifications()
                clipboardHandler.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error in connecting", e)
                _isConnected.value = false
            }
        }
    }

    private fun stop() {
        scope.launch {
            writeChannel?.close()
            socket?.close()
            _isConnected.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private val mutex = Mutex() // Mutex to control write access
    suspend fun sendMessage(message: SocketMessage) {
        // Only one coroutine at a time can acquire the lock and send the message
        mutex.withLock {
            try {
                if (_isConnected.value) {
                    writeChannel?.let { channel ->
                        val jsonMessage = messageSerializer.serialize(message)
                        channel.writeStringUtf8("$jsonMessage\n") // Add newline to separate messages
                        channel.flush()
                        Log.d(TAG, "Message sent successfully")
                    } ?: run {
                        Log.e(TAG, "Write channel is not available")
                    }
                } else {
                    // Disconnected
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _isConnected.value = false
            }
        }
    }

    private suspend fun startListening() {
        try {
            Log.d(TAG, "listening started")
            val receiveChannel = socket?.openReadChannel()
            receiveChannel?.let { channel ->
                while (!channel.isClosedForRead) {
                    try {
                        // Read the incoming data as a line
                        val receivedData = channel.readUTF8Line()

                        receivedData?.let { jsonMessage ->
                            Log.d(TAG, "Raw received data: $jsonMessage")
                            messageSerializer.deserialize(jsonMessage).also { socketMessage->
                                socketMessage?.let { handleMessage(it) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error while receiving data")
                        e.printStackTrace()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Session error")
            e.printStackTrace()
        } finally {
            Log.d(TAG, "Session closed")
            stop()
        }
    }

    companion object {
        enum class Actions {
            START,
            STOP
        }
        const val ACTION_OPEN_MAIN = "android.intent.action.MAIN"
        const val TAG = "NetworkService"
        const val DEVICE_INFO = "device_info"
        const val REMOTE_INFO = "remote_info"
    }
}