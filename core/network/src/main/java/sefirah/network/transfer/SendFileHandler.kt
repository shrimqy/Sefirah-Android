package sefirah.network.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import io.ktor.utils.io.cancel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.streams.asByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import sefirah.common.R
import sefirah.domain.model.FileMetadata
import sefirah.network.util.formatSize
import java.io.IOException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Handles sending files to a remote device.
 */
class SendFileHandler(
    private val context: Context,
    private val transferId: String,
    private val serverSocket: SSLServerSocket,
    private val fileUris: List<Uri>,
    private val filesMetadata: List<FileMetadata>,
    private val deviceName: String,
    private val notifications: TransferNotificationHelper
) {
    val totalBytes: Long = filesMetadata.sumOf { it.fileSize }
    private var totalBytesTransferred: Long = 0

    suspend fun send() {
        var sslSocket: SSLSocket? = null
        try {
            val title = context.getString(
                R.string.notification_sending_title_format,
                context.getString(R.string.notification_sending_action),
                fileUris.size,
                if (fileUris.size == 1) {
                    context.getString(R.string.notification_file)
                } else {
                    context.getString(R.string.notification_files)
                },
                context.getString(R.string.notification_to),
                deviceName
            )
            
            notifications.showProgress(transferId = transferId, title = title)

            sslSocket = withContext(Dispatchers.IO) {
                serverSocket.accept() as? SSLSocket
            } ?: throw IOException("Failed to accept SSL connection")

            val readChannel = sslSocket.inputStream.toByteReadChannel()
            val writeChannel = sslSocket.outputStream.asByteWriteChannel()

            fileUris.forEachIndexed { index, fileUri ->
                currentCoroutineContext().ensureActive()

                withTimeout(5000) {
                    if (readChannel.readUTF8Line() != TRANSFER_START_MESSAGE) throw IOException("Invalid transfer handshake")
                }

                val metadata = filesMetadata[index]

                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        
                        writeChannel.writeFully(buffer, 0, bytesRead)
                        writeChannel.flush()
                        totalBytesTransferred += bytesRead

                        val progress = ((totalBytesTransferred.toFloat() / totalBytes) * 100).toInt()
                        val title = context.getString(
                            R.string.notification_sending_title_format,
                            context.getString(R.string.notification_sending_action),
                            fileUris.size,
                            if (fileUris.size == 1) {
                                context.getString(R.string.notification_file)
                            } else {
                                context.getString(R.string.notification_files)
                            },
                            context.getString(R.string.notification_to),
                            deviceName
                        )
                        
                        val fileInfo = if (fileUris.size > 1) {
                            "${metadata.fileName} (${index + 1}/${fileUris.size})"
                        } else {
                            metadata.fileName
                        }
                        
                        val progressText = context.getString(
                            R.string.notification_progress_format,
                            progress,
                            formatSize(totalBytesTransferred),
                            formatSize(totalBytes)
                        )

                        notifications.updateProgress(
                            transferId = transferId,
                            title = title,
                            subText = progressText,
                            contentText = fileInfo,
                            progress = progress
                        )
                    }
                }

                val message = readChannel.readUTF8Line()
                if (message != TRANSFER_COMPLETE_MESSAGE) {
                    throw IOException("Invalid transfer confirmation: '$message'")
                }
            }

            notifications.showCompleted(transferId, fileUris.size)

            writeChannel.flushAndClose()
            readChannel.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            if (e !is kotlinx.coroutines.CancellationException) {
                notifications.showError(transferId, e.message ?: "Transfer failed")
            }
            throw e
        } finally {
            sslSocket?.close()
            withContext(Dispatchers.IO) { serverSocket.close() }
        }
    }

    companion object {
        private const val TAG = "SendFileHandler"
        private const val BUFFER_SIZE = 131072 * 4 // 512 KB
        const val TRANSFER_START_MESSAGE = "start"
        const val TRANSFER_COMPLETE_MESSAGE = "complete"
    }
}
