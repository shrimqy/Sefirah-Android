package sefirah.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sefirah.database.AppRepository
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.FileMetadata
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.ServerInfo
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class ClipboardHandler @Inject constructor(
    private val context: Context,
    private val socketFactory: SocketFactory,
    private val appRepository: AppRepository
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    fun setClipboard(clipboard: ClipboardMessage) {
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = when {
                clipboard.clipboardType == "text/plain" -> {
                    ClipData.newPlainText("Received clipboard", clipboard.content)
                }

                clipboard.clipboardType.startsWith("image/") -> {
                    val imageBytes = Base64.decode(clipboard.content, Base64.DEFAULT)
                    val extension = clipboard.clipboardType.substringAfter('/').lowercase()
                    val tempFile = File.createTempFile("sefirah_clipboard_image", ".$extension", context.cacheDir).apply {
                        deleteOnExit()
                    }

                    FileOutputStream(tempFile).use { outputStream ->
                        outputStream.write(imageBytes)
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    ClipData.newUri(context.contentResolver, "Received image", uri)
                }

                else -> {
                    ClipData.newPlainText("Received clipboard", clipboard.content)
                }
            }
            clipboardManager.setPrimaryClip(clip)
        } catch (ex: Exception) {
            Log.e("ClipboardHandler", "Exception handling clipboard", ex)
        }
    }

    fun handleFileTransfer(fileTransfer: FileTransfer) {
        serviceScope.launch {
            try {
                receiveFileTransfer(fileTransfer.serverInfo, fileTransfer.fileMetadata)
            } catch (e: Exception) {
                Log.e("ClipboardHandler", "File transfer failed", e)
            }
        }
    }

    private suspend fun receiveFileTransfer(serverInfo: ServerInfo, fileMetadata: FileMetadata) {
        try {
            val lastConnectedDevice = appRepository.getLastConnectedDevice()
            val clientSocket = socketFactory.tcpClientSocket(
                SocketType.FILE_TRANSFER,
                lastConnectedDevice!!.prefAddress!!,
                serverInfo.port,
            ) ?: throw IOException("Failed to establish connection")

            val readChannel = clientSocket.openReadChannel()
            val writeChannel = clientSocket.openWriteChannel()
            
            try {
                writeChannel.writeStringUtf8(serverInfo.password)
                writeChannel.flush()
                
                val fileUri = receiveFileInternal(
                    readChannel = readChannel,
                    metadata = fileMetadata
                )
                
                writeChannel.writeStringUtf8("Complete")
                writeChannel.flush()

                // Add the received file to clipboard
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newUri(context.contentResolver, "Received file", fileUri))
                
                Log.d("ClipboardHandler", "File transfer completed and added to clipboard: ${fileMetadata.fileName}")
            } finally {
                // Clean up resources
                try {
                    clientSocket.close()
                } catch (e: Exception) {
                    Log.e("ClipboardHandler", "Error closing socket", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ClipboardHandler", "File transfer failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun receiveFileInternal(
        readChannel: io.ktor.utils.io.ByteReadChannel,
        metadata: FileMetadata
    ): Uri {
        var tempFile: File? = null
        try {
            // Create temp file like existing clipboard handling
            val extension = metadata.fileName.substringAfterLast('.', "")
            val fileName = if (extension.isNotEmpty()) {
                "sefirah_clipboard_file.$extension"
            } else {
                "sefirah_clipboard_file"
            }
            
            tempFile = File.createTempFile("sefirah_clipboard_file", ".$extension", context.cacheDir).apply {
                deleteOnExit()
            }

            FileOutputStream(tempFile).use { output ->
                val fileSize = metadata.fileSize
                var currentFileReceived = 0L

                val buffer = ByteArray(131072 * 4) // 512 KB buffer

                while (currentFileReceived < fileSize) {
                    val bytesToRead = minOf(buffer.size.toLong(), fileSize - currentFileReceived)

                    val bytesRead = readChannel.copyTo(output, bytesToRead)
                    if (bytesRead <= 0) break

                    currentFileReceived += bytesRead
                }

                if (currentFileReceived != fileSize) {
                    throw IOException("Incomplete transfer: received $currentFileReceived bytes out of $fileSize.")
                }
                Log.d("ClipboardHandler", "Transfer completed successfully: $currentFileReceived/$fileSize bytes")
            }
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            return uri
        } catch (e: Exception) {
            Log.e("ClipboardHandler", "File transfer failed: ${e.message}", e)
            tempFile?.let { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (deleteException: Exception) {
                    Log.e("ClipboardHandler", "Failed to delete incomplete file", deleteException)
                }
            }
            throw e
        }
    }
}
