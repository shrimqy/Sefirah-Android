package sefirah.network

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
import sefirah.domain.interfaces.SocketFactory
import sefirah.network.util.NetworkHelper.localAddress
import sefirah.network.util.TrustManager
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

@Singleton
class SocketFactoryImpl @Inject constructor(
    private val customTrustManager: TrustManager,
) : SocketFactory {
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun tcpClientSocket(address: String, port: Int): Socket? {
        return coroutineScope {
            try {
                Log.d(TAG, "Connecting to $address:$port")
                withTimeoutOrNull(3000L) {
                    aSocket(selectorManager).tcp().connect(address, port).tls(scope.coroutineContext) {
                        trustManager = customTrustManager.getRemoteTrustManager()
                    }
                }?.also { 
                    Log.d(TAG, "Connected successfully to ${it.remoteAddress}")
                } ?: run {
                    Log.e(TAG, "Connection timed out")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                null
            }
        }
    }

    override suspend fun tcpServerSocket(range: IntRange): SSLServerSocket? {
        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(
            customTrustManager.getLocalKeyManagerFactory().keyManagers,
            arrayOf(customTrustManager.getLocalTrustManager()),
            SecureRandom()
        )

        range.forEach { port ->
            try {
                val serverSocket = withContext(Dispatchers.IO) {
                    sslContext.serverSocketFactory.createServerSocket(port)
                } as SSLServerSocket
                Log.d(TAG, "Server socket created on ${localAddress}:${port}")
                return serverSocket
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create server socket on port $port", e)
            }
        }
        Log.e(TAG, "Server socket creation failed")
        return null
    }

    override suspend fun udpSocket(port: Int): BoundDatagramSocket {
        return try {
            val selectorManager = SelectorManager(Dispatchers.IO)
            aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", port)) {
                reuseAddress = true
                broadcast = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create UDP server", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "SocketFactory"
    }
}