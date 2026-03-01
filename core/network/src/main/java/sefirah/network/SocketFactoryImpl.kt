package sefirah.network

import android.util.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sefirah.domain.interfaces.SocketFactory
import sefirah.network.util.NetworkHelper.localAddress
import sefirah.network.util.SslHelper
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@Singleton
class SocketFactoryImpl @Inject constructor() : SocketFactory {
    val selectorManager = SelectorManager(Dispatchers.IO)

    override suspend fun tcpClientSocket(address: String, port: Int, certificate: ByteArray?): SSLSocket? {
        return try {
            Log.d(TAG, "Connecting with client cert to $address:$port")
            val sslContext = SslHelper.sslContext(certificate)
            withTimeoutOrNull(3000L) {
                withContext(Dispatchers.IO) {
                    (sslContext.socketFactory.createSocket(address, port) as SSLSocket).apply {
                        startHandshake()
                    }
                }
            }?.also {
                Log.d(TAG, "Connected with client cert to ${it.remoteSocketAddress}")
            } ?: run {
                Log.e(TAG, "Connection timed out to $address:$port")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed to $address:$port", e)
            null
        }
    }

    override suspend fun tcpServerSocket(range: IntRange, certificate: ByteArray?): SSLServerSocket? {
        val sslContext = SslHelper.sslContext(certificate)

        range.forEach { port ->
            try {
                val serverSocket = withContext(Dispatchers.IO) {
                    sslContext.serverSocketFactory.createServerSocket(port)
                } as SSLServerSocket

                serverSocket.needClientAuth = true

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