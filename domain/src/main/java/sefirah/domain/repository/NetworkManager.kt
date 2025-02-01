package sefirah.domain.repository

import kotlinx.coroutines.flow.Flow
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.SocketMessage

interface NetworkManager {
    suspend fun sendMessage(message: SocketMessage)
    val connectionState: Flow<ConnectionState>
}