package sefirah.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sefirah.domain.model.SocketMessage
import sefirah.domain.repository.NetworkManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkManagerImpl @Inject constructor(
    private val context: Context
) : NetworkManager {
    private val serviceIntent by lazy { Intent(context, NetworkService::class.java) }
    private var networkService: NetworkService? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: Flow<Boolean> = _isConnected.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            networkService = (service as NetworkService.LocalBinder).getService()

            // Immediately sync the connection state when service connects
//           _isConnected.value = networkService?.isConnected?.value ?: false

            // Start collecting connection state from service
            scope.launch {
                networkService?.isConnected?.collect { connected ->
                    _isConnected.value = connected
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            networkService = null
            _isConnected.value = false
        }
    }

    init {
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override suspend fun sendMessage(message: SocketMessage) {
        networkService?.sendMessage(message)
    }
}