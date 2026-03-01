package sefirah.domain.interfaces

import io.ktor.network.sockets.BoundDatagramSocket
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

interface SocketFactory {
    suspend fun tcpClientSocket(address: String, port: Int, certificate: ByteArray? = null): SSLSocket?
    suspend fun tcpServerSocket(range: IntRange, certificate: ByteArray? = null): SSLServerSocket?
    suspend fun udpSocket(port: Int): BoundDatagramSocket
}


