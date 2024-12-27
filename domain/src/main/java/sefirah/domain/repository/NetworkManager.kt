package sefirah.domain.repository

import kotlinx.coroutines.flow.Flow
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.ConnectionState

interface NetworkManager {
    suspend fun sendMessage(message: SocketMessage)
    val connectionState: Flow<ConnectionState>
}