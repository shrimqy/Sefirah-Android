package sefirah.domain.interfaces

import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Socket
import javax.net.ssl.SSLServerSocket

interface SocketFactory {
    suspend fun tcpClientSocket(address: String, port: Int): Socket?
    suspend fun tcpServerSocket(range: IntRange): SSLServerSocket?
    suspend fun udpSocket(port: Int): BoundDatagramSocket
}


