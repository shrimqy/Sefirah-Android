package sefirah.domain.repository

import kotlinx.coroutines.flow.StateFlow
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.SocketMessage

interface NetworkManager {
    val pendingDeviceApproval: StateFlow<PendingDeviceApproval?>
    
    suspend fun connectPaired(device: PairedDevice)
    suspend fun connectTo(connectionDetails: ConnectionDetails)
    suspend fun disconnect(deviceId: String)
    fun broadcastMessage(message: SocketMessage)
    fun sendMessage(deviceId: String, message: SocketMessage)
    fun sendClipboardMessage(message: ClipboardMessage)
    suspend fun approveDeviceConnection(deviceId: String)
    suspend fun rejectDeviceConnection(deviceId: String)
}