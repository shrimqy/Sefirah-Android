package sefirah.domain.repository

import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketType
import javax.net.ssl.SSLServerSocket

interface SocketFactory {
    suspend fun createSocket(type: SocketType, remoteDevice: RemoteDevice): Result<Socket>
    suspend fun createServerSocket(type: SocketType, port: Int, ipAddress: String): Result<ServerSocket>
    suspend fun createServer(port: Int, ipAddress: String): SSLServerSocket
}


