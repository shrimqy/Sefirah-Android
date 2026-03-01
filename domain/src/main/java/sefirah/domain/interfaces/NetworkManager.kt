package sefirah.domain.interfaces

import sefirah.domain.model.ClipboardInfo
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.SocketMessage

interface NetworkManager {
    suspend fun connectPaired(device: PairedDevice)
    suspend fun connectTo(connectionDetails: ConnectionDetails)
    suspend fun disconnect(deviceId: String)
    fun broadcastMessage(message: SocketMessage)
    fun sendMessage(deviceId: String, message: SocketMessage)
    fun sendClipboardMessage(message: ClipboardInfo)
    suspend fun approveDeviceConnection(deviceId: String)
    suspend fun rejectDeviceConnection(deviceId: String)
}