package sefirah.network.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sefirah.domain.model.FileTransferMessage
import sefirah.domain.repository.DeviceManager
import sefirah.domain.model.ServerInfo
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.clipboard.ClipboardHandler
import sefirah.network.util.generateRandomPassword
import sefirah.network.util.getFileMetadata
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
    private val notifications: TransferNotificationHelper,
    private val networkManager: NetworkManager,
    private val clipboardHandler: ClipboardHandler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTransfers = ConcurrentHashMap<String, Job>()

    fun sendFiles(deviceId: String, fileUris: List<Uri>) {
        val transferId = UUID.randomUUID().toString()

        val job = scope.launch {
            try {
                val device = deviceManager.getPairedDevice(deviceId)
                    ?: throw IOException("Device $deviceId not found")
                
                val filesMetadata = fileUris.map { getFileMetadata(context, it) }
                
                val serverSocket = socketFactory.tcpServerSocket(PORT_RANGE)
                    ?: throw IOException("Failed to create server socket")

                val serverInfo = ServerInfo(serverSocket.localPort, generateRandomPassword())
                
                val handler = SendFileHandler(
                    context = context,
                    transferId = transferId,
                    serverSocket = serverSocket,
                    fileUris = fileUris,
                    filesMetadata = filesMetadata,
                    password = serverInfo.password,
                    deviceName = device.deviceName,
                    notifications = notifications
                )

                networkManager.sendMessage(deviceId, FileTransferMessage(serverInfo, filesMetadata))
                handler.send()
            } catch (e: CancellationException) {
                Log.d(TAG, "Transfer $transferId cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Send files failed", e)
            } finally {
                activeTransfers.remove(transferId)
            }
        }
        activeTransfers[transferId] = job
    }

    fun receiveFiles(deviceId: String, transfer: FileTransferMessage) {
        val transferId = UUID.randomUUID().toString()

        val job = scope.launch {
            try {
                val device = deviceManager.getPairedDevice(deviceId)
                    ?: throw IOException("Device $deviceId not found")

                val address = device.address 
                    ?: throw IOException("No connected address for device $deviceId")

                val clientSocket = socketFactory.tcpClientSocket(address, transfer.serverInfo.port)
                    ?: throw IOException("Failed to establish connection")

                val handler = ReceiveFileHandler(
                    context = context,
                    transferId = transferId,
                    clientSocket = clientSocket,
                    serverInfo = transfer.serverInfo,
                    files = transfer.files,
                    deviceName = device.deviceName,
                    preferencesRepository = if (transfer.isClipboard) null else preferencesRepository,
                    notifications = if (transfer.isClipboard) null else notifications
                )

                val fileUri = handler.receive()
                fileUri?.let { clipboardHandler.setClipboardUri(it) }
            } catch (e: CancellationException) {
                Log.d(TAG, "Transfer $transferId cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Receive files failed", e)
            } finally {
                activeTransfers.remove(transferId)
            }
        }
        activeTransfers[transferId] = job
    }

    fun cancelTransfer(transferId: String) {
        activeTransfers[transferId]?.cancel()
        notifications.cancel(transferId)
        activeTransfers.remove(transferId)
    }

    companion object {
        private const val TAG = "FileTransferManager"
        val PORT_RANGE = 5152..5169
    }
}
