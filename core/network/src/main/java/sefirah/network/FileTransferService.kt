package sefirah.network

import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
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
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.database.AppRepository
import sefirah.domain.model.BulkFileTransfer
import sefirah.domain.model.FileMetadata
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.ServerInfo
import sefirah.domain.model.SocketType
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.MessageSerializer
import sefirah.network.util.generateRandomPassword
import sefirah.network.util.getDeviceIpAddress
import sefirah.network.util.getFileMetadata
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
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
    @Inject lateinit var appRepository: AppRepository

    private var serverSocket: SSLServerSocket? = null
    private var socket: Socket? = null
    private var clientSocket: io.ktor.network.sockets.Socket? = null
    private var fileInputStream: InputStream? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private sealed class TransferType {
        data class Sending(val fileName: String) : TransferType()
        data class Receiving(
            val fileName: String,
            val currentFileIndex: Int = 0,
            val totalFiles: Int = 1
        ) : TransferType()
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
                    }
                }
            }

            ACTION_RECEIVE_FILE -> {
                val fileTransfer = intent.getParcelableExtra<FileTransfer>(EXTRA_FILE_TRANSFER)
                val bulkTransfer = intent.getParcelableExtra<BulkFileTransfer>(EXTRA_BULK_TRANSFER)
                
                when {
                    fileTransfer != null -> {
                        serviceScope.launch {
                            try {
                                Log.d(TAG, "Starting single file transfer process")
                                receiveSingleFile(fileTransfer)
                            } catch (e: Exception) {
                                Log.e(TAG, "File transfer failed", e)
                                updateNotificationForError(e.message ?: "Transfer failed")
                            }
                        }
                    }
                    bulkTransfer != null -> {
                        serviceScope.launch {
                            try {
                                Log.d(TAG, "Starting bulk file transfer process")
                                receiveBulkFiles(bulkTransfer)
                            } catch (e: Exception) {
                                Log.e(TAG, "Bulk file transfer failed", e)
                                updateNotificationForError(e.message ?: "Transfer failed")
                            }
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
            val password = generateRandomPassword()
            val serverInfo = initializeServer(password) ?: return
            fileInputStream = this.contentResolver.openInputStream(fileUri)
            val fileMetadata = getFileMetadata(this, fileUri)

            val message = FileTransfer(serverInfo, fileMetadata)
            networkManager.sendMessage(message)

            withContext(Dispatchers.IO) {
                socket = serverSocket?.accept() as? SSLSocket
                    ?: throw IOException("Failed to accept SSL connection")

                val outputStream = socket!!.getOutputStream()
                val inputStream = socket!!.getInputStream()

                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                withTimeout(5000) {
                    if (reader.readLine() != password) throw IOException("Invalid password")
                }

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesTransferred = 0L

                try {
                    // Send the file data
                    while (fileInputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()

                        totalBytesTransferred += bytesRead
                        updateTransferProgress(totalBytesTransferred, fileMetadata.fileSize)
                    }

                    // Signal end of file stream
                    outputStream.flush()
                    socket?.shutdownOutput()

                    withTimeout(5000) {
                        if (reader.readLine() == "Complete") {
                            delay(1000)
                            withContext(Dispatchers.Main) {
                                showCompletedNotification()
                            }
                        } else {
                            throw IOException("Invalid confirmation received")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during file transfer", e)
                    throw IOException("File transfer interrupted", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "File transfer failed", e)
            updateNotificationForError(e.message ?: "Transfer failed")
            throw e
        } finally {
            cleanup()
            stopSelf()
        }
    }

    private suspend fun initializeServer(password: String): ServerInfo? {
        val ipAddress = getDeviceIpAddress()
        PORT_RANGE.forEach { port ->
            try {
                serverSocket = ipAddress?.let { socketFactory.tcpServerSocket(port, it) } ?: return null
                Log.d(TAG, "Server socket created on ${ipAddress}:${port}, waiting for client to connect")


                return ServerInfo(ipAddress, port, password)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create server socket on port $port, trying next port", e)
            }
        }
        Log.e(TAG, "Server socket creation failed")
        return null
    }

    private suspend fun receiveSingleFile(fileTransfer: FileTransfer) {
        setupNotification(TransferType.Receiving(fileTransfer.fileMetadata.fileName))
        
        // For single file, establish connection here
        val lastConnectedDevice = appRepository.getLastConnectedDevice()
        clientSocket = socketFactory.tcpClientSocket(
            SocketType.FILE_TRANSFER,
            lastConnectedDevice!!.prefAddress!!,
            fileTransfer.serverInfo.port,
        ) ?: throw IOException("Failed to establish connection")

        val readChannel = clientSocket!!.openReadChannel()
        val writeChannel = clientSocket!!.openWriteChannel()
        try {
            writeChannel.writeStringUtf8(fileTransfer.serverInfo.password)
            writeChannel.flush()
            val fileUri = receiveFileInternal(
                readChannel = readChannel,
                metadata = fileTransfer.fileMetadata
            )
            writeChannel.writeStringUtf8("Success")
            writeChannel.flush()

            showCompletedNotification(fileUri = fileUri, mimeType = fileTransfer.fileMetadata.mimeType)
            
            if (preferencesRepository.readImageClipboardSettings().first()) {
                val clipboardManager = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newUri(contentResolver, "received file", fileUri))
            }
        } finally {
            cleanup()
            stopSelf()
        }
    }

    private suspend fun receiveBulkFiles(bulkTransfer: BulkFileTransfer) {
        var totalReceived = 0L
        val totalSize = bulkTransfer.files.sumOf { it.fileSize }
        
        // Establish connection once for all files
        val lastConnectedDevice = appRepository.getLastConnectedDevice()
        clientSocket = socketFactory.tcpClientSocket(
            SocketType.FILE_TRANSFER,
            lastConnectedDevice!!.prefAddress!!,
            bulkTransfer.serverInfo.port,
        ) ?: throw IOException("Failed to establish connection")

        val readChannel = clientSocket!!.openReadChannel()
        val writeChannel = clientSocket!!.openWriteChannel()

        try {
            writeChannel.writeStringUtf8(bulkTransfer.serverInfo.password)
            writeChannel.flush()
            bulkTransfer.files.forEachIndexed { index, metadata ->
                try {
                    setupNotification(TransferType.Receiving(
                        fileName = metadata.fileName,
                        currentFileIndex = index + 1,
                        totalFiles = bulkTransfer.files.size
                    ))
                    
                    receiveFileInternal(
                        readChannel = readChannel,
                        metadata = metadata,
                        totalReceived = totalReceived,
                        totalSize = totalSize
                    )
                    writeChannel.writeStringUtf8("Success")
                    writeChannel.flush()
                    totalReceived += metadata.fileSize
                    
                    showCompletedNotification(
                        currentFile = index + 1,
                        totalFiles = bulkTransfer.files.size,
                        fileName = metadata.fileName
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to receive file: ${metadata.fileName}", e)
                    updateNotificationForError(
                        error = "Failed to receive ${metadata.fileName}",
                        currentFile = index + 1,
                        totalFiles = bulkTransfer.files.size
                    )
                    throw e  // Rethrow to stop the bulk transfer
                }
            }
            showBulkTransferCompletedNotification(bulkTransfer.files.size)
        } finally {
            cleanup()
            stopSelf()
        }
    }

    private suspend fun receiveFileInternal(
        readChannel: io.ktor.utils.io.ByteReadChannel,
        metadata: FileMetadata,
        totalReceived: Long = 0,
        totalSize: Long = metadata.fileSize
    ) : Uri {
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
                        put(MediaStore.Downloads.MIME_TYPE, metadata.mimeType)
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
                var currentFileReceived = 0L
                var lastProgressUpdate = 0L

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (currentFileReceived < fileSize) {
                    val bytesToRead = minOf(buffer.size.toLong(), fileSize - currentFileReceived).toInt()
                    val bytesRead = readChannel.readAvailable(buffer, 0, bytesToRead)

                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    currentFileReceived += bytesRead

                    val progressThreshold = maxOf(DEFAULT_BUFFER_SIZE.toLong(), fileSize / 100)
                    if (currentFileReceived - lastProgressUpdate >= progressThreshold || currentFileReceived >= fileSize) {
                        updateTransferProgress(
                            currentFileReceived = currentFileReceived,
                            currentFileSize = fileSize,
                            totalReceived = totalReceived + currentFileReceived,
                            totalSize = totalSize
                        )
                        lastProgressUpdate = currentFileReceived
                        Log.d(TAG, "Progress: $currentFileReceived/$fileSize bytes (${(currentFileReceived * 100 / fileSize)}%)")
                    }
                }

                if (currentFileReceived != fileSize) {
                    throw IOException("Incomplete transfer: received $currentFileReceived bytes out of $fileSize.")
                }
                Log.d(TAG, "Transfer completed successfully: $currentFileReceived/$fileSize bytes")
            }
            return fileUri
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
            throw e
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

    private fun updateTransferProgress(
        currentFileReceived: Long,
        currentFileSize: Long,
        totalReceived: Long = currentFileReceived,
        totalSize: Long = currentFileSize
    ) {
        val currentProgress = ((currentFileReceived.toFloat() / currentFileSize) * 100).toInt()
        val totalProgress = ((totalReceived.toFloat() / totalSize) * 100).toInt()
        
        val currentMB = currentFileReceived / 1024f / 1024f
        val currentTotalMB = currentFileSize / 1024f / 1024f
        
        when (val transfer = currentTransfer) {
            is TransferType.Receiving -> {
                val progressText = if (transfer.totalFiles > 1) {
                    "File ${transfer.currentFileIndex}/${transfer.totalFiles}: %.1f MB / %.1f MB\nTotal Progress: $totalProgress%%"
                        .format(currentMB, currentTotalMB)
                } else {
                    "Receiving: %.1f MB / %.1f MB".format(currentMB, currentTotalMB)
                }
                
                notificationCenter.modifyNotification(notificationBuilder, AppNotifications.TRANSFER_PROGRESS_ID) {
                    setProgress(100, currentProgress, false)
                    setContentText(progressText)
                }
            }
            is TransferType.Sending -> {
                val progressText = "Sending: %.1f MB / %.1f MB".format(currentMB, currentTotalMB)
                
                notificationCenter.modifyNotification(notificationBuilder, AppNotifications.TRANSFER_PROGRESS_ID) {
                    setProgress(100, currentProgress, false)
                    setContentText(progressText)
                }
            }
        }
    }

    private fun showCompletedNotification(
        currentFile: Int? = null,
        totalFiles: Int? = null,
        fileName: String? = null,
        fileUri: Uri? = null,
        mimeType: String? = null
    ) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        val title = when {
            currentFile != null && totalFiles != null -> 
                "File $currentFile of $totalFiles Complete"
            else -> "File Transfer Complete"
        }
        
        val contentText = when {
            fileName != null -> "Successfully received $fileName"
            else -> "Transfer completed successfully"
        }

        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_COMPLETE_CHANNEL,
            notificationId = AppNotifications.TRANSFER_COMPLETE_ID,
        ) {
            setContentTitle(title)
            setContentText(contentText)
            setOngoing(false)
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
            setSilent(false)

            if (fileUri != null && mimeType != null) {
                val openFileIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
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

    private fun showBulkTransferCompletedNotification(totalFiles: Int) {
        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_COMPLETE_CHANNEL,
            notificationId = AppNotifications.TRANSFER_COMPLETE_ID,
        ) {
            setContentTitle("Transfer Complete")
            setContentText("Successfully received $totalFiles files")
            setOngoing(false)
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setSilent(false)
        }
    }

    private fun updateNotificationForError(
        error: String,
        currentFile: Int? = null,
        totalFiles: Int? = null
    ) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val title = when {
            currentFile != null && totalFiles != null -> 
                "File Transfer Error (File $currentFile of $totalFiles)"
            else -> "File Transfer Error"
        }

        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_ERROR_CHANNEL,
            notificationId = AppNotifications.TRANSFER_ERROR_ID,
        ) {
            setContentTitle(title)
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
            clientSocket?.close()
            socket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    companion object {

        const val ACTION_SEND_FILE = "sefirah.network.action.SEND_FILE"
        const val ACTION_RECEIVE_FILE = "sefirah.network.action.RECEIVE_FILE"
        const val EXTRA_FILE_TRANSFER = "sefirah.network.extra.FILE_TRANSFER"
        const val EXTRA_BULK_TRANSFER = "sefirah.network.extra.BULK_TRANSFER"
        const val ACTION_CANCEL_TRANSFER = "sefirah.network.action.CANCEL_TRANSFER"

        private const val TAG = "FileTransferService"

        private val PORT_RANGE = 5152..5169
        private const val DEFAULT_BUFFER_SIZE = 131072 * 4 // 512 KB

        private const val CANCEL_REQUEST_CODE = 100
    }
}
