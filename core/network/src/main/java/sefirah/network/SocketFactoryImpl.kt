package sefirah.network

import android.content.Context
import android.util.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.tls.tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.TrustManager
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.coroutines.coroutineContext


class SocketFactoryImpl @Inject constructor(
    val context: Context,
    private val customTrustManager: TrustManager,
): SocketFactory {

    override suspend fun tcpClientSocket(
        type: SocketType,
        ipAddress: String,
        port: Int,
    ): Socket? {
        return try {
            Log.d("connect", "Connecting to $ipAddress:$port")
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).tcp()
                .connect(ipAddress, port)
                .tls(coroutineContext) {
                    trustManager = customTrustManager.getRemoteTrustManager()
                }

            Log.d("connect", "Client Connected to $ipAddress")
            socket
        } catch (e: Exception) {
            Log.e("connect", "Failed to connect", e)
            null
        }
    }

    override suspend fun tcpServerSocket(port: Int, ipAddress: String): SSLServerSocket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            customTrustManager.getLocalKeyManagerFactory().keyManagers,
            arrayOf(customTrustManager.getLocalTrustManager()),
            SecureRandom()
        )

        return withContext(Dispatchers.IO) {
            sslContext.serverSocketFactory.createServerSocket(
                port,
                50,
                InetAddress.getByName(ipAddress)
            )
        } as SSLServerSocket
    }

    override fun udpSocket(port: Int): BoundDatagramSocket {
        return try {
            val selectorManager = SelectorManager(Dispatchers.IO)
            aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", port)) {
                reuseAddress = true
                broadcast = true
            }
        } catch (e: Exception) {
            Log.e("SocketFactory", "Failed to create UDP server", e)
            throw e
        }
    }
}