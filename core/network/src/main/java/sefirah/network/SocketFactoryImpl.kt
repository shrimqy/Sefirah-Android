package sefirah.network

import android.content.Context
import android.util.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.tls.tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.TrustManager
import java.net.InetAddress
import java.security.SecureRandom
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.coroutines.coroutineContext
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory


class SocketFactoryImpl @Inject constructor(
    val context: Context,
    private val customTrustManager: TrustManager,
): SocketFactory {

    override suspend fun createSocket(
        type: SocketType,
        remoteDevice: RemoteDevice,
    ): Result<Socket> {
        return try {
            Log.d("connect", "Trying to connect")
            val selectorManager = SelectorManager(Dispatchers.IO)
            val socket = aSocket(selectorManager).tcp()
                .connect(remoteDevice.ipAddress, remoteDevice.port)
                .tls(coroutineContext) {
                    trustManager = customTrustManager.getTrustManager()
                }

            Log.d("connect", "Client Connected to ${remoteDevice.ipAddress}")
            Result.success(socket)
        } catch (e: Exception) {
            Log.e("connect", "Failed to connect", e)
            Result.failure(e)
        }
    }

    override suspend fun createServerSocket(type: SocketType, port: Int, ipAddress: String): Result<ServerSocket> {
        return try {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp().bind(ipAddress, port)
            Result.success(serverSocket)
        } catch (e: Exception) {
            Log.e("SocketFactory", "Failed to create server socket", e)
            Result.failure(e)
        }
    }

    override suspend fun createServer(port: Int, ipAddress: String): SSLServerSocket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            customTrustManager.getKeyManagerFactory().keyManagers,
            arrayOf(customTrustManager.getTrustManager()),
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
}