package sefirah.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.ktor.utils.io.cancel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.streams.asByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import sefirah.common.R
import sefirah.common.notifications.NotificationCenter
import sefirah.common.util.createTempFileUri
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.FileMetadata
import java.io.File
import java.io.IOException
import javax.net.ssl.SSLSocket

/**
 * Handles receiving files from a remote device.
 * @param notificationCenter If null, no notifications are shown.
 * @param useTempStorage If true, write to a temp file (clipboard); otherwise use Downloads/custom storage.
 */
class ReceiveFileHandler(
    private val context: Context,
    private val transferId: String,
    private val clientSocket: SSLSocket,
    private val files: List<FileMetadata>,
    private val deviceName: String,
    private val preferencesRepository: PreferencesRepository? = null,
    notificationCenter: NotificationCenter? = null,
    private val useTempStorage: Boolean = false,
) {
    private val totalBytes: Long = files.sumOf { it.fileSize }
    private var totalBytesReceived: Long = 0

    private val notification: TransferNotification? = notificationCenter?.let {
        TransferNotification(context, transferId, it)
    }

    suspend fun receive(): Uri? {
        val readChannel = clientSocket.inputStream.toByteReadChannel()
        val writeChannel = clientSocket.outputStream.asByteWriteChannel()
        var lastFileUri: Uri? = null

        try {
            notification?.let {
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
                it.showPreparing(title)
            }

            files.forEachIndexed { index, metadata ->
                currentCoroutineContext().ensureActive()

                lastFileUri = receiveFile(readChannel, writeChannel, metadata, index + 1)
            }

            notification?.showCompleted(
                files.size,
                fileUri = if (files.size == 1) lastFileUri else null,
                mimeType = if (files.size == 1) files.first().mimeType else null
            )

            return lastFileUri
        } catch (e: CancellationException) {
            notification?.cancel()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receive failed", e)
            notification?.showError(e.message ?: "Transfer failed")
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
            writeChannel.writeStringUtf8(SendFileHandler.TRANSFER_START_MESSAGE)
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

                    notification?.updateProgress(
                        bytesTransferred = totalBytesReceived,
                        totalBytes = totalBytes,
                        fileName = metadata.fileName,
                        fileIndex = fileIndex,
                        fileCount = files.size
                    )
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
        if (useTempStorage) {
            val extension = metadata.mimeType
                .substringAfter('/')
                .substringBefore(';')
                .lowercase()
                .ifEmpty { "png" }
            return createTempFileUri(context, FileMetadata.CLIPBOARD_FILE_NAME, extension)
        }

        // Normal mode -> storage location or Downloads
        return when {
            preferencesRepository?.getStorageLocation()?.first()?.isNotEmpty() == true -> {
                val storageUri = preferencesRepository.getStorageLocation().first().toUri()
                val directory = DocumentFile.fromTreeUri(context, storageUri)
                    ?: throw IOException("Failed to access custom storage")
                val fileName = getUniqueFileName(metadata.fileName) { candidate ->
                    directory.findFile(candidate) != null
                }
                directory.createFile(metadata.mimeType, fileName)?.uri
                    ?: throw IOException("Failed to create file in custom storage")
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val fileName = getUniqueFileName(metadata.fileName) { candidate ->
                    downloadFileExists(candidate)
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, metadata.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Failed to create MediaStore entry")
            }

            else -> {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = getUniqueFileName(metadata.fileName) { candidate ->
                    File(downloadsDir, candidate).exists()
                }
                val file = File(downloadsDir, fileName)
                if (!withContext(Dispatchers.IO) {
                        file.createNewFile()
                    }) throw IOException("Failed to create file in Downloads")
                Uri.fromFile(file)
            }
        }
    }

    private fun getUniqueFileName(
        fileName: String,
        exists: (String) -> Boolean
    ): String {
        if (!exists(fileName)) return fileName

        val lastDotIndex = fileName.lastIndexOf('.')
        val hasExtension = lastDotIndex > 0 && lastDotIndex < fileName.lastIndex
        val baseName = if (hasExtension) fileName.substring(0, lastDotIndex) else fileName
        val extension = if (hasExtension) fileName.substring(lastDotIndex) else ""

        var copyIndex = 1
        var candidate: String
        do {
            candidate = "$baseName ($copyIndex)$extension"
            copyIndex++
        } while (exists(candidate))

        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadFileExists(fileName: String): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, "${Environment.DIRECTORY_DOWNLOADS}/")

        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    companion object {
        private const val TAG = "ReceiveFileHandler"
        private const val BUFFER_SIZE = 131072 * 4 // 512 KB
    }
}
