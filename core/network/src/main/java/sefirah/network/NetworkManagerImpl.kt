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
import sefirah.domain.model.ConnectionState
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

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            networkService = (service as NetworkService.LocalBinder).getService()

            scope.launch {
                networkService?.connectionState?.collect { state ->
                    _connectionState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            networkService = null
            _connectionState.value = ConnectionState.Disconnected()
        }
    }

    init {
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override suspend fun sendMessage(message: SocketMessage) {
        networkService?.sendMessage(message)
    }
}