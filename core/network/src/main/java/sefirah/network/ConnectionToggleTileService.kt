package sefirah.network

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.common.R
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.PairedDevice
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class ConnectionToggleTileService : TileService() {
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var deviceManager: DeviceManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectionStateJob: kotlinx.coroutines.Job? = null
    private var connectionState: ConnectionState = ConnectionState.Disconnected()
    private var lastConnectedDevice: PairedDevice? = null
    
    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }
    
    override fun onStartListening() {
        super.onStartListening()
        // Start observing connection state for the last connected device
        connectionStateJob = serviceScope.launch {
            deviceManager.pairedDevices.collectLatest { devices ->
                val device = devices.maxByOrNull { it.lastConnected ?: 0L }
                lastConnectedDevice = device
                connectionState = device?.connectionState ?: ConnectionState.Disconnected()
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        connectionStateJob?.cancel()
        connectionStateJob = null
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch(Dispatchers.IO) {
           lastConnectedDevice?.let { device ->
               when {
                   connectionState.isConnected -> {
                       networkManager.disconnect(device.deviceId)
                   }
                   connectionState.isDisconnected -> {
                       networkManager.connectPaired(device)
                   }
               }
           }
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        serviceScope.cancel()
    }
    
    private fun updateTile() {
        serviceScope.launch(Dispatchers.IO) {
            val tile = qsTile ?: return@launch

            val (label, tileState, icon, subtitle) = when {
                connectionState.isConnected -> {
                    TileData(
                        label = lastConnectedDevice?.deviceName ?: "",
                        tileState = Tile.STATE_ACTIVE,
                        subtitle = getString(R.string.status_connected),
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, R.drawable.ic_sync),
                    )
                }

                connectionState.isDisconnected -> {
                    if (lastConnectedDevice != null) {
                        TileData(
                            label = lastConnectedDevice!!.deviceName,
                            tileState = Tile.STATE_INACTIVE,
                            subtitle = getString(R.string.status_disconnected),
                            icon = Icon.createWithResource(this@ConnectionToggleTileService, R.drawable.ic_sync_disabled),
                        )
                    } else {
                        TileData(
                            label = getString(R.string.no_device),
                            tileState = Tile.STATE_UNAVAILABLE,
                            icon = Icon.createWithResource(this@ConnectionToggleTileService, R.drawable.ic_sync_desktop),
                        )
                    }
                }

                connectionState.isError -> {
                    TileData(
                        label = getString(R.string.error),
                        tileState = Tile.STATE_UNAVAILABLE,
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, R.drawable.ic_sync_problem),
                    )
                }
                else -> {
                    TileData(
                        label = "Unknown",
                        tileState = Tile.STATE_UNAVAILABLE,
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, R.drawable.ic_sync_problem),
                    )
                }
            }

            withContext(Dispatchers.Main) {
                tile.label = label
                tile.icon = icon
                tile.state = tileState
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = subtitle
                }
                tile.updateTile()
            }
        }
    }

    private data class TileData(
        val label: String,
        val tileState: Int,
        val icon: Icon,
        val subtitle: String? = null,
    )

    companion object {
        private const val TAG = "ConnectionTileService"
    }
}