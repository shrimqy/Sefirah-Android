package sefirah.network

import android.content.Context
import android.util.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.tls.tls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.TrustManager
import java.net.InetAddress
import java.security.SecureRandom
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket


class SocketFactoryImpl @Inject constructor(
    val context: Context,
    private val customTrustManager: TrustManager,
) : SocketFactory {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun tcpClientSocket(
        type: SocketType,
        ipAddress: String,
        port: Int,
    ): Socket? {
        return coroutineScope {
            try {
                Log.d("SocketFactory", "Connecting to $ipAddress:$port")
                withTimeoutOrNull(3000L) {
                    aSocket(selectorManager).tcp().connect(ipAddress, port).tls(connectionScope.coroutineContext) {
                        trustManager = customTrustManager.getRemoteTrustManager()
                    }
                }?.also { 
                    Log.d("SocketFactory", "Connected successfully to ${it.remoteAddress}")
                } ?: run {
                    Log.e("SocketFactory", "Connection timed out")
                    null
                }
            } catch (e: Exception) {
                Log.e("SocketFactory", "Connection failed", e)
                null
            }
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

    override suspend fun udpSocket(port: Int): BoundDatagramSocket {
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