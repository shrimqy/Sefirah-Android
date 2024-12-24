package sefirah.domain.repository

import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketType

interface SocketFactory {
    suspend fun createSocket(type: SocketType, remoteDevice: RemoteDevice): Result<Socket>
    suspend fun createServerSocket(type: SocketType): Result<ServerSocket>
}


