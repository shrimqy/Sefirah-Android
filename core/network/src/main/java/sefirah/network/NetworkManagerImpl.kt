package sefirah.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.SocketMessage
import sefirah.domain.interfaces.NetworkManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkManagerImpl @Inject constructor(
    private val context: Context
) : NetworkManager {
    private val serviceIntent by lazy { Intent(context, NetworkService::class.java) }
    private var networkService: NetworkService? = null

    private val emptyPendingFlow = MutableStateFlow<PendingDeviceApproval?>(null)
    override val pendingDeviceApproval: StateFlow<PendingDeviceApproval?>
        get() = networkService?.pendingDeviceApproval ?: emptyPendingFlow

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            networkService = (service as NetworkService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            networkService = null
        }
    }

    init {
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override suspend fun connectPaired(device: PairedDevice) {
        networkService?.connectPaired(device)
    }

    override suspend fun connectTo(connectionDetails: ConnectionDetails) {
        networkService?.connectTo(connectionDetails)
    }

    override suspend fun disconnect(deviceId: String) {
        networkService?.disconnect(deviceId)
    }

    override fun broadcastMessage(message: SocketMessage) {
        networkService?.broadcastMessage(message)
    }

    override fun sendMessage(deviceId: String, message: SocketMessage) {
        networkService?.sendMessage(deviceId, message)
    }

    override fun sendClipboardMessage(message: ClipboardMessage) {
        networkService?.sendClipboardMessage(message)
    }

    override suspend fun approveDeviceConnection(deviceId: String) {
        networkService?.approveDeviceConnection(deviceId)
    }

    override suspend fun rejectDeviceConnection(deviceId: String) {
        networkService?.rejectDeviceConnection(deviceId)
    }
}