package sefirah.network

import android.content.Context
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
        Log.d("connect", "Trying to connect")
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(remoteDevice.ipAddress, remoteDevice.port).tls(
            coroutineContext
        ) {
            trustManager = customTrustManager.getTrustManager()
        }

        Log.d("connect", "Client Connected to ${remoteDevice.ipAddress}")
        return Result.success(socket)
    }

    override suspend fun createServerSocket(type: SocketType): Result<ServerSocket> {
        TODO("Not yet implemented")
    }
}