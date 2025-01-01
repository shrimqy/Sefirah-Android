package sefirah.network

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.domain.model.FileTransferInfo
import sefirah.domain.model.ServerInfo
import sefirah.domain.model.SocketType
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.MessageSerializer
import sefirah.network.util.getDeviceIpAddress
import sefirah.network.util.getFileMetadata
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import javax.inject.Inject
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@AndroidEntryPoint
class FileTransferService : Service() {
    @Inject lateinit var socketFactory: SocketFactory
    @Inject lateinit var messageSerializer: MessageSerializer
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var notificationCenter: NotificationCenter
    @Inject lateinit var preferencesRepository: PreferencesRepository

    private var serverSocket: SSLServerSocket? = null
    private var socket: Socket? = null
    private var clientSocket: io.ktor.network.sockets.Socket? = null
    private var fileInputStream: InputStream? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private sealed class TransferType {
        data class Sending(val fileName: String) : TransferType()
        data class Receiving(val fileName: String) : TransferType()
    }

    private lateinit var currentTransfer: TransferType

    override fun onBind(intent: Intent?): IBinder? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_CANCEL_TRANSFER -> {
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SEND_FILE -> {
                val fileUri = intent.data ?: run {
                    Log.e(TAG, "No URI provided in intent")
                    return START_NOT_STICKY
                }
                serviceScope.launch {
                    try {
                        Log.d(TAG, "Starting file transfer process")
                        sendFile(fileUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "File transfer failed", e)
                        updateNotificationForError(e.message ?: "Transfer failed")
                    } finally {
                        Log.d(TAG, "Transfer process completed, stopping service")
                        stopSelf()
                    }
                }
            }

            ACTION_RECEIVE_FILE -> {
                val fileTransferInfo = intent.getParcelableExtra<FileTransferInfo>(EXTRA_FILE_TRANSFER_INFO)
                fileTransferInfo?.let {
                    serviceScope.launch {
                        try {
                            Log.d(TAG, "Starting file transfer process")
                            receiveFile(fileTransferInfo)
                        } catch (e: Exception) {
                            Log.e(TAG, "File transfer failed", e)
                            updateNotificationForError(e.message ?: "Transfer failed")
                        } finally {
                            Log.d(TAG, "Transfer process completed, stopping service")
                        }
                    }

                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun sendFile(fileUri: Uri) {
        val fileName = getFileMetadata(this, fileUri).fileName
        setupNotification(TransferType.Sending(fileName))
        try {
            val serverInfo = initializeServer() ?: return
            fileInputStream = this.contentResolver.openInputStream(fileUri)
            val fileMetadata = getFileMetadata(this, fileUri)

            val message = FileTransferInfo(serverInfo, fileMetadata)
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
            updateNotificationForError(e.message ?: "Transfer failed")
            throw e
        } finally {
            cleanup()
        }
    }

    private suspend fun initializeServer(): ServerInfo? {
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

    private suspend fun receiveFile(fileTransferInfo: FileTransferInfo) {
        setupNotification(TransferType.Receiving(fileTransferInfo.metadata.fileName))
        clientSocket = socketFactory.createSocket(
            SocketType.FILE_TRANSFER,
            fileTransferInfo.serverInfo.ipAddress,
            fileTransferInfo.serverInfo.port
        ).getOrNull()
        val readChannel = clientSocket?.openReadChannel() ?: throw IOException("Failed to open read channel")
        val metadata = fileTransferInfo.metadata
        var fileUri: Uri? = null

        try {
            // Create the output file URI
            fileUri = when {
                preferencesRepository.getStorageLocation().first().isNotEmpty() -> {
                    val storageUri = preferencesRepository.getStorageLocation().first().toUri()
                    val directory = DocumentFile.fromTreeUri(this@FileTransferService, storageUri)
                    directory?.createFile(metadata.mimeType, metadata.fileName)?.uri
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, metadata.fileName)
                        put(MediaStore.Downloads.MIME_TYPE, metadata.mimeType ?: "*/*")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                }
                else -> {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, metadata.fileName).apply {
                        if (!exists()) createNewFile()
                    }
                    Uri.fromFile(file)
                }
            } ?: throw IOException("Failed to create output file")

            contentResolver.openOutputStream(fileUri)?.use { output ->
                val fileSize = metadata.fileSize
                var totalBytesReceived = 0L

                while (totalBytesReceived < fileSize) {
                    val bytesToRead = minOf(DEFAULT_BUFFER_SIZE.toLong(), fileSize - totalBytesReceived)
                    val bytesRead = readChannel.copyTo(output, bytesToRead)

                    if (bytesRead == 0L) break  // End of stream
                    totalBytesReceived += bytesRead
                    updateTransferProgress(totalBytesReceived, fileSize)
                }

                if (totalBytesReceived != fileSize) {
                    throw IOException("Incomplete transfer: received $totalBytesReceived bytes out of $fileSize.")
                }

                Log.d(TAG, "Transfer completed successfully: $totalBytesReceived/$fileSize bytes")
            }
            showCompletedNotification(fileUri, metadata.mimeType)
        } catch (e: Exception) {
            Log.e(TAG, "File transfer failed: ${e.message}", e)
            fileUri?.let { uri ->
                try {
                    contentResolver.delete(uri, null, null)
                } catch (deleteException: Exception) {
                    Log.e(TAG, "Failed to delete incomplete file", deleteException)
                }
            }
            updateNotificationForError(e.message ?: "Transfer failed")
        } finally {
            cleanup()
        }
    }

    private fun setupNotification(transferType: TransferType) {
        currentTransfer = transferType
        
        val title = when (transferType) {
            is TransferType.Sending -> "Sending ${transferType.fileName}"
            is TransferType.Receiving -> "Receiving ${transferType.fileName}"
        }

        // Create explicit intent for cancel action
        val cancelIntent = Intent(this, FileTransferService::class.java).apply { 
            action = ACTION_CANCEL_TRANSFER
        }
        
        // Update PendingIntent flags and request code
        val pendingCancelIntent = PendingIntent.getService(
            this,
            CANCEL_REQUEST_CODE,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_PROGRESS_CHANNEL,
            notificationId = AppNotifications.TRANSFER_PROGRESS_ID,
        ) {
            setContentTitle(title)
            setContentText("Preparing transfer...")
            setProgress(0, 0, true)
            setOngoing(true)
            setSilent(true)
            setAutoCancel(false)
            clearActions()
            addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.cancel),
                pendingCancelIntent
            )
        }
        startForeground(AppNotifications.TRANSFER_PROGRESS_ID, notificationBuilder.build())
    }

    private fun updateTransferProgress(transferred: Long, total: Long) {
        val progress = ((transferred.toFloat() / total) * 100).toInt()
        val transferredMB = transferred / 1024f / 1024f
        val totalMB = total / 1024f / 1024f

        val actionText = when (currentTransfer) {
            is TransferType.Sending -> "Sending"
            is TransferType.Receiving -> "Receiving"
        }
        notificationCenter.modifyNotification(notificationBuilder, AppNotifications.TRANSFER_PROGRESS_ID) {
            setProgress(100, progress, false)
            setContentText("$actionText: %.1f MB / %.1f MB".format(transferredMB, totalMB))
        }
    }

    private fun showCompletedNotification(fileUri: Uri? = null, mimeType: String? = null) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val fileName = when (currentTransfer) {
            is TransferType.Sending -> (currentTransfer as TransferType.Sending).fileName
            is TransferType.Receiving -> (currentTransfer as TransferType.Receiving).fileName
        }

        val contentText = when (currentTransfer) {
            is TransferType.Sending -> "Successfully sent $fileName"
            is TransferType.Receiving -> "Successfully saved $fileName"
        }

        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_COMPLETE_CHANNEL,
            notificationId = AppNotifications.TRANSFER_COMPLETE_ID,
        ) {
            setContentTitle(when (currentTransfer) {
                is TransferType.Sending -> "File Sent"
                is TransferType.Receiving -> "File Received"
            })
            setContentText(contentText)
            setOngoing(false)
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setSilent(currentTransfer is TransferType.Sending)

            if (currentTransfer is TransferType.Receiving && fileUri != null) {
                val openFileIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType ?: "*/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                val pendingIntent = PendingIntent.getActivity(
                    this@FileTransferService,
                    0,
                    openFileIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setContentIntent(pendingIntent)
            }
        }
    }

    private fun updateNotificationForError(error: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_ERROR_CHANNEL,
            notificationId = AppNotifications.TRANSFER_ERROR_ID,
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

        const val ACTION_SEND_FILE = "sefirah.network.action.SEND_FILE"
        const val ACTION_RECEIVE_FILE = "sefirah.network.action.RECEIVE_FILE"
        const val EXTRA_FILE_TRANSFER_INFO = "sefirah.network.extra.FILE_TRANSFER_INFO"
        const val ACTION_CANCEL_TRANSFER = "sefirah.network.action.CANCEL_TRANSFER"

        private const val TAG = "FileTransferService"

        private val PORT_RANGE = 9000..9069
        private const val DEFAULT_BUFFER_SIZE = 81920 * 5

        private const val CANCEL_REQUEST_CODE = 100
    }
}
