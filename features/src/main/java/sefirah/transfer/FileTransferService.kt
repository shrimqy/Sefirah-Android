package sefirah.transfer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardFeature
import sefirah.common.notifications.NotificationCenter
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.interfaces.SocketFactory
import sefirah.domain.model.ClipboardTransfer
import sefirah.domain.model.FileMetadata
import sefirah.domain.model.FileTransferSession
import sefirah.domain.model.ServerInfo
import sefirah.domain.model.ShareTransfer
import sefirah.domain.model.SmsAttachmentTransfer
import sefirah.domain.model.SocketMessage
import sefirah.transfer.util.getFileMetadata
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTransferService @Inject constructor(
    private val context: Context,
    private val socketFactory: SocketFactory,
    private val deviceManager: DeviceManager,
    private val preferencesRepository: PreferencesRepository,
    private val notificationCenter: NotificationCenter,
    private val networkManager: NetworkManager,
    private val clipboardFeature: ClipboardFeature,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTransfers = ConcurrentHashMap<String, Job>()

    fun sendFiles(deviceId: String, fileUris: List<Uri>) {
        val files = fileUris.map { uri ->
            val metadata = getFileMetadata(context, uri)
            TransferSource(metadata) {
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Failed to open $uri")
            }
        }
        send(deviceId, files, TransferKind.Share)
    }

    /**
     * Stream [pfd] as a clipboard image. Takes ownership of [pfd] and closes it when finished.
     */
    fun sendClipboard(deviceId: String, pfd: ParcelFileDescriptor, metadata: FileMetadata) {
        send(deviceId, pfdSource(pfd, metadata), TransferKind.Clipboard) {
            runCatching { pfd.close() }
        }
    }

    /**
     * Stream [pfd] as an SMS attachment. Takes ownership of [pfd] and closes it when finished.
     */
    fun sendSmsAttachment(
        deviceId: String,
        pfd: ParcelFileDescriptor,
        metadata: FileMetadata,
        partId: Long,
    ) {
        send(deviceId, pfdSource(pfd, metadata), TransferKind.SmsAttachment(partId)) {
            runCatching { pfd.close() }
        }
    }

    fun receiveShare(deviceId: String, transfer: ShareTransfer) {
        receive(deviceId, transfer.transfer, TransferKind.Share)
    }

    fun receiveClipboard(deviceId: String, transfer: ClipboardTransfer) {
        receive(deviceId, transfer.transfer, TransferKind.Clipboard)
    }

    private fun pfdSource(pfd: ParcelFileDescriptor, metadata: FileMetadata) = listOf(
        TransferSource(metadata) { FileInputStream(pfd.fileDescriptor) },
    )

    private fun send(
        deviceId: String,
        files: List<TransferSource>,
        kind: TransferKind,
        onFinished: (() -> Unit)? = null,
    ) {
        val transferId = UUID.randomUUID().toString()

        activeTransfers[transferId] = scope.launch {
            try {
                val device = deviceManager.getPairedDevice(deviceId)
                    ?: throw IOException("Device $deviceId not found")

                val serverSocket = socketFactory.tcpServerSocket(PORT_RANGE, device.certificate)
                    ?: throw IOException("Failed to create server socket")

                val session = FileTransferSession(files.map { it.metadata }, ServerInfo(serverSocket.localPort))
                val handler = SendFileHandler(
                    context = context,
                    transferId = transferId,
                    serverSocket = serverSocket,
                    files = files,
                    deviceName = device.deviceName,
                    notificationCenter = if (kind.showNotification) notificationCenter else null,
                )

                networkManager.sendMessage(deviceId, kind.toMessage(session))
                handler.send()
            } catch (e: CancellationException) {
                Log.d(TAG, "Transfer $transferId cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Send files failed", e)
            } finally {
                activeTransfers.remove(transferId)
                onFinished?.invoke()
            }
        }
    }

    private fun receive(
        deviceId: String,
        session: FileTransferSession,
        kind: TransferKind,
    ) {
        val transferId = UUID.randomUUID().toString()

        activeTransfers[transferId] = scope.launch {
            try {
                val device = deviceManager.getPairedDevice(deviceId)
                    ?: throw IOException("Device $deviceId not found")

                val address = device.address
                    ?: throw IOException("No connected address for device $deviceId")

                val clientSocket = socketFactory.tcpClientSocket(address, session.serverInfo.port, device.certificate)
                    ?: throw IOException("Failed to establish connection")

                val handler = ReceiveFileHandler(
                    context = context,
                    transferId = transferId,
                    clientSocket = clientSocket,
                    files = session.files,
                    deviceName = device.deviceName,
                    preferencesRepository = if (kind.showNotification) preferencesRepository else null,
                    notificationCenter = if (kind.showNotification) notificationCenter else null,
                    useTempStorage = kind is TransferKind.Clipboard,
                )

                val fileUri = handler.receive() ?: return@launch
                val setClipboard = kind is TransferKind.Clipboard ||
                    (kind is TransferKind.Share &&
                        preferencesRepository.readImageClipboardSettingsForDevice(deviceId).first())
                if (setClipboard) clipboardFeature.setClipboardUri(fileUri)
            } catch (e: CancellationException) {
                Log.d(TAG, "Transfer $transferId cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Receive files failed", e)
            } finally {
                activeTransfers.remove(transferId)
            }
        }
    }

    fun cancelTransfer(transferId: String) {
        activeTransfers[transferId]?.cancel()
        activeTransfers.remove(transferId)
    }

    /**
     * Share: notified + Downloads/custom storage.
     * Clipboard: silent + temp file + set clipboard.
     * SmsAttachment: send-only (silent).
     */
    private sealed class TransferKind {
        data object Share : TransferKind()
        data object Clipboard : TransferKind()
        data class SmsAttachment(val partId: Long) : TransferKind()

        val showNotification: Boolean get() = this is Share

        fun toMessage(session: FileTransferSession): SocketMessage = when (this) {
            Share -> ShareTransfer(session)
            Clipboard -> ClipboardTransfer(session)
            is SmsAttachment -> SmsAttachmentTransfer(session, partId)
        }
    }

    companion object {
        private const val TAG = "FileTransferService"
        val PORT_RANGE = 5152..5169
        const val ACTION_CANCEL_TRANSFER = "CANCEL_TRANSFER"
        const val EXTRA_TRANSFER_ID = "extra_transfer_id"
    }
}
