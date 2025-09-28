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
import sefirah.domain.model.ServerInfo
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.TrustManager
import sefirah.network.util.generateRandomPassword
import sefirah.network.util.getDeviceIpAddress
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
                Log.d(TAG, "Connecting to $ipAddress:$port")
                withTimeoutOrNull(3000L) {
                    aSocket(selectorManager).tcp().connect(ipAddress, port).tls(connectionScope.coroutineContext) {
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

    override suspend fun tcpServerSocket(range: IntRange): Pair<ServerInfo, SSLServerSocket>? {
        val ipAddress = getDeviceIpAddress() ?: return null
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            customTrustManager.getLocalKeyManagerFactory().keyManagers,
            arrayOf(customTrustManager.getLocalTrustManager()),
            SecureRandom()
        )

        range.forEach { port ->
            try {
                val serverSocket = withContext(Dispatchers.IO) {
                    sslContext.serverSocketFactory.createServerSocket(
                        port,
                        50,
                        InetAddress.getByName(ipAddress)
                    )
                } as SSLServerSocket
                Log.d(TAG, "Server socket created on ${ipAddress}:${port}, waiting for client to connect")
                return Pair(ServerInfo(ipAddress, port, generateRandomPassword()), serverSocket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create server socket on port $port, trying next port", e)
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