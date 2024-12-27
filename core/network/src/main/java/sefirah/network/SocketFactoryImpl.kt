package sefirah.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.tls.tls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketType
import sefirah.domain.repository.SocketFactory
import sefirah.network.util.TrustManager
import sefirah.network.util.getDeviceIpAddress
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class SocketFactoryImpl @Inject constructor(
    val context: Context,
    private val customTrustManager: TrustManager
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

    override suspend fun createServerSocket(type: SocketType): Result<ServerSocket> {
        return try {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = getDeviceIpAddress()?.let { ipAddress ->
                aSocket(selectorManager).tcp().bind(ipAddress, 9002)
            } ?: throw IllegalStateException("Could not get device IP address")

            Result.success(serverSocket)
        } catch (e: Exception) {
            Log.e("SocketFactory", "Failed to create server socket", e)
            Result.failure(e)
        }
    }
}