package sefirah.domain.repository

import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketType
import java.security.cert.X509Certificate
import javax.net.ssl.SSLServerSocket

interface SocketFactory {
    suspend fun tcpClientSocket(type: SocketType, ipAddress: String, port: Int): Socket?
    suspend fun tcpServerSocket(port: Int, ipAddress: String): SSLServerSocket
    suspend fun udpSocket(port: Int): BoundDatagramSocket
}


