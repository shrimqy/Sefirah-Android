package sefirah.network

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.common.extensions.NotificationCenter
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.ServerInfo
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.MessageSerializer
import sefirah.network.util.getDeviceIpAddress
import sefirah.network.util.getFileMetadata
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import javax.inject.Inject
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import sefirah.common.R

@AndroidEntryPoint
class FileTransferService : Service() {
    @Inject lateinit var socketFactory: SocketFactory
    @Inject lateinit var messageSerializer: MessageSerializer
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var notificationCenter: NotificationCenter

    private var serverSocket: SSLServerSocket? = null
    private var socket: Socket? = null
    private var fileInputStream: InputStream? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileUri = intent?.data ?: run {
            Log.e(TAG, "No URI provided in intent")
            return START_NOT_STICKY
        }
        
        Log.d(TAG, "Service started with URI: $fileUri")
        startForeground(NOTIFICATION_ID, notificationBuilder.build())

        serviceScope.launch {
            try {
                Log.d(TAG, "Starting file transfer process")
                sendFile(fileUri)
            } catch (e: Exception) {
                Log.e(TAG, "File transfer failed", e)
                updateNotificationForError(e.message ?: "Transfer failed")
            } finally {
                Log.d(TAG, "Transfer process completed, stopping service")
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun sendFile(fileUri: Uri) {
        try {
            val serverInfo = initialize() ?: return
            fileInputStream = this.contentResolver.openInputStream(fileUri)
            val fileMetadata = getFileMetadata(this, fileUri)

            val message = FileTransfer(serverInfo, fileMetadata)
            networkManager.sendMessage(message)

            withContext(Dispatchers.IO) {
                socket = serverSocket?.accept() as? SSLSocket
                    ?: throw IOException("Failed to accept SSL connection")

                val outputStream = socket?.getOutputStream()
                    ?: throw IOException("Failed to get output stream")

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesTransferred = 0L

                while (fileInputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    outputStream.flush()

                    totalBytesTransferred += bytesRead
                    updateTransferProgress(totalBytesTransferred, fileMetadata.fileSize)
                }
                showCompletedNotification()
            }
        } catch (e: Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            updateNotificationForError(e.message ?: "Transfer failed")
            throw e
        } finally {
            cleanup()
        }
    }

    private suspend fun initialize(): ServerInfo? {
        val ipAddress = getDeviceIpAddress()
        PORT_RANGE.forEach { port ->
            try {
                serverSocket = ipAddress?.let { socketFactory.createServer(port, it) } ?: return null
                Log.d(TAG, "Server socket created on ${ipAddress}:${port}, waiting for client to connect")
                return ServerInfo(ipAddress, port)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create server socket on port $port, trying next port", e)
            }
        }
        Log.e(TAG, "Server socket creation failed")
        return null
    }

    private fun setupNotification() {
        notificationBuilder = notificationCenter.showNotification(
            channelId = FILE_TRANSFER_CHANNEL_ID,
            channelName = getString(R.string.notification_file_transfer),
            notificationId = NOTIFICATION_ID,
            importance = NotificationManager.IMPORTANCE_LOW 
        ) {
            setContentTitle("File Transfer")
            setContentText("Preparing transfer...")
            setProgress(0, 0, true)
            setOngoing(true)
            setSilent(true) 
        }
    }

    private fun updateTransferProgress(transferred: Long, total: Long) {
        val progress = ((transferred.toFloat() / total) * 100).toInt()
        val transferredMB = transferred / 1024f / 1024f
        val totalMB = total / 1024f / 1024f

        notificationCenter.modifyNotification(notificationBuilder, NOTIFICATION_ID) {
            setProgress(100, progress, false)
            setContentText("Transferring: %.1f MB / %.1f MB".format(transferredMB, totalMB))
        }
    }


    private fun showCompletedNotification() {
        notificationCenter.modifyNotification(notificationBuilder, NOTIFICATION_ID) {
            setProgress(0, 0, false)
            setContentText("File transfer complete")
            setOngoing(false)
            setAutoCancel(true)
            setSilent(false)
            setPriority(NotificationCompat.PRIORITY_HIGH)
        }
    }

    private fun updateNotificationForError(error: String) {
        // Error notification with sound
        notificationCenter.showNotification(
            channelId = ERROR_CHANNEL_ID,
            channelName = getString(R.string.notification_file_transfer),
            notificationId = ERROR_NOTIFICATION_ID,
            importance = NotificationManager.IMPORTANCE_HIGH
        ) {
            setContentTitle("File Transfer Error")
            setContentText(error)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setAutoCancel(true)
            setSilent(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cleanup()
    }

    private fun cleanup() {
        try {
            fileInputStream?.close()
            socket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    companion object {
        private const val TAG = "FileTransferService"

        private const val NOTIFICATION_ID = 1001
        private const val ERROR_NOTIFICATION_ID = 1003

        private const val FILE_TRANSFER_CHANNEL_ID = "file_transfer_channel"
        private const val ERROR_CHANNEL_ID = "file_transfer_error_channel"

        private val PORT_RANGE = 9000..9069
        private const val DEFAULT_BUFFER_SIZE = 81920
    }
}