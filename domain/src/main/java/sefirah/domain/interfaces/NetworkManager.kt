package sefirah.domain.interfaces

import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.SocketMessage

interface NetworkManager {
    fun startService()
    fun stopService()
    suspend fun connectPaired(device: PairedDevice)
    suspend fun connectTo(connectionDetails: ConnectionDetails)
    suspend fun disconnect(deviceId: String)
    fun broadcastMessage(message: SocketMessage)
    /** @return false if there is no live connection */
    fun sendMessage(deviceId: String, message: SocketMessage): Boolean
    suspend fun approveDeviceConnection(deviceId: String)
    suspend fun rejectDeviceConnection(deviceId: String)
}