package sefirah.network.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.ktor.utils.io.cancel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.streams.asByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import sefirah.common.R
import sefirah.common.util.createTempFileUri
import sefirah.domain.model.FileMetadata
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.network.util.formatSize
import java.io.File
import java.io.IOException
import javax.net.ssl.SSLSocket

/**
 * Handles receiving files from a remote device.
 * @param notifications Optional notification helper. If null, no notifications are shown (e.g., clipboard transfers).
 */
class ReceiveFileHandler(
    private val context: Context,
    private val transferId: String,
    private val clientSocket: SSLSocket,
    private val files: List<FileMetadata>,
    private val deviceName: String,
    private val preferencesRepository: PreferencesRepository? = null,
    private val notifications: TransferNotificationHelper? = null
) {
    val totalBytes: Long = files.sumOf { it.fileSize }
    private var totalBytesReceived: Long = 0
    
    var lastFileUri: Uri? = null
        private set

    private val isSilent: Boolean get() = notifications == null

    suspend fun receive(): Uri? {
        val readChannel = clientSocket.inputStream.toByteReadChannel()
        val writeChannel = clientSocket.outputStream.asByteWriteChannel()

        try {
            notifications?.let {
                val title = context.getString(
                    R.string.notification_receiving_title_format,
                    context.getString(R.string.notification_receiving_action),
                    files.size,
                    if (files.size == 1) {
                        context.getString(R.string.notification_file)
                    } else {
                        context.getString(R.string.notification_files)
                    },
                    context.getString(R.string.notification_from),
                    deviceName
                )
                
                it.showProgress(transferId = transferId, title = title)
            }

            files.forEachIndexed { index, metadata ->
                currentCoroutineContext().ensureActive()

                lastFileUri = receiveFile(readChannel, writeChannel, metadata, index + 1)
            }

            notifications?.showCompleted(
                transferId,
                files.size,
                fileUri = if (files.size == 1) lastFileUri else null,
                mimeType = if (files.size == 1) files.first().mimeType else null
            )

            return lastFileUri
        } catch (e: Exception) {
            Log.e(TAG, "Receive failed", e)
            if (e !is kotlinx.coroutines.CancellationException) {
                notifications?.showError(transferId, e.message ?: "Transfer failed")
            }
            throw e
        } finally {
            readChannel.cancel()
            writeChannel.flushAndClose()
            try { clientSocket.close() } catch (_: Exception) { }
        }
    }

    private suspend fun receiveFile(
        readChannel: io.ktor.utils.io.ByteReadChannel,
        writeChannel: io.ktor.utils.io.ByteWriteChannel,
        metadata: FileMetadata,
        fileIndex: Int
    ): Uri {
        val fileUri = createOutputUri(metadata)
        
        try {
            // Send "start" message to indicate we're ready to receive this file
            writeChannel.writeStringUtf8("start")
            writeChannel.flush()
            
            context.contentResolver.openOutputStream(fileUri)?.use { output ->
                var currentFileReceived = 0L
                val buffer = ByteArray(BUFFER_SIZE)

                while (currentFileReceived < metadata.fileSize) {
                    currentCoroutineContext().ensureActive()

                    val bytesToRead = minOf(buffer.size.toLong(), metadata.fileSize - currentFileReceived).toInt()
                    val bytesRead = readChannel.readAvailable(buffer, 0, bytesToRead)

                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    currentFileReceived += bytesRead
                    totalBytesReceived += bytesRead

                    notifications?.let {
                        val progress = ((totalBytesReceived.toFloat() / totalBytes) * 100).toInt()
                        val title = context.getString(
                            R.string.notification_receiving_title_format,
                            context.getString(R.string.notification_receiving_action),
                            files.size,
                            if (files.size == 1) {
                                context.getString(R.string.notification_file)
                            } else {
                                context.getString(R.string.notification_files)
                            },
                            context.getString(R.string.notification_from),
                            deviceName
                        )
                        
                        val fileInfo = if (files.size > 1) {
                            "${metadata.fileName} ($fileIndex/${files.size})"
                        } else {
                            metadata.fileName
                        }
                        
                        val progressText = context.getString(
                            R.string.notification_progress_format,
                            progress,
                            formatSize(totalBytesReceived),
                            formatSize(totalBytes)
                        )

                        it.updateProgress(
                            transferId = transferId,
                            title = title,
                            subText = progressText,
                            contentText = fileInfo,
                            progress = progress
                        )
                    }
                }

                if (currentFileReceived != metadata.fileSize) {
                    throw IOException("Incomplete transfer: received $currentFileReceived bytes out of ${metadata.fileSize}")
                }

                writeChannel.writeStringUtf8(SendFileHandler.TRANSFER_COMPLETE_MESSAGE)
                writeChannel.flush()
            } ?: throw IOException("Failed to open output stream")
            
            return fileUri
        } catch (e: Exception) {
            try { context.contentResolver.delete(fileUri, null, null) }
            catch (_: Exception) { }
            throw e
        }
    }

    private suspend fun createOutputUri(metadata: FileMetadata): Uri {
        // Silent mode (clipboard) -> temp file URI
        if (isSilent) {
            val extension = metadata.fileName.substringAfterLast('.', "")
            return createTempFileUri(context, "sefirah_clipboard", extension)
        }
        
        // Normal mode -> storage location or Downloads
        return when {
            preferencesRepository?.getStorageLocation()?.first()?.isNotEmpty() == true -> {
                val storageUri = preferencesRepository.getStorageLocation().first().toUri()
                val directory = DocumentFile.fromTreeUri(context, storageUri)
                directory?.createFile(metadata.mimeType, metadata.fileName)?.uri
                    ?: throw IOException("Failed to create file in custom storage")
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, metadata.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, metadata.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Failed to create MediaStore entry")
            }

            else -> {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, metadata.fileName).apply {
                    if (!exists()) createNewFile()
                }
                Uri.fromFile(file)
            }
        }
    }

    companion object {
        private const val TAG = "ReceiveFileHandler"
        private const val BUFFER_SIZE = 131072 * 4 // 512 KB
    }
}
