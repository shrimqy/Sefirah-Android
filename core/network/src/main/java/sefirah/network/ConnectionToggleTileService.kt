package sefirah.network

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.database.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.ConnectionState
import sefirah.domain.repository.NetworkManager
import javax.inject.Inject
import sefirah.common.R as CommonR

@AndroidEntryPoint
class ConnectionToggleTileService : TileService() {
    
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var networkManager: NetworkManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectionStateJob: kotlinx.coroutines.Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }
    
    override fun onStartListening() {
        super.onStartListening()
        // Start observing connection state
        connectionStateJob = serviceScope.launch {
            networkManager.connectionState.collectLatest { state ->
                updateTile()
                _connectionState.value = state
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
        Log.d(TAG, "Tile clicked")
        
        serviceScope.launch(Dispatchers.IO) {
            val lastDevice = appRepository.getLastConnectedDevice()
            when {
                connectionState.value.isConnected -> {
                    val stopIntent = Intent(this@ConnectionToggleTileService, NetworkService::class.java).apply {
                        action = NetworkService.Companion.Actions.STOP.name
                    }
                    startForegroundService(stopIntent)
                }
                connectionState.value.isDisconnected && lastDevice != null -> {
                    val intent = Intent(this@ConnectionToggleTileService, NetworkService::class.java).apply {
                        action = NetworkService.Companion.Actions.START.name
                        putExtra(NetworkService.REMOTE_INFO, lastDevice.toDomain())
                    }
                    startForegroundService(intent)
                }
                connectionState.value.isDisconnected && lastDevice == null -> {
                }
                connectionState.value.isConnecting -> {
                }
            }
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "Tile removed")
        serviceScope.cancel()
    }
    
    private fun updateTile() {
        serviceScope.launch(Dispatchers.IO) {
            val tile = qsTile ?: return@launch

            val (label, tileState, icon, subtitle) = when {
                connectionState.value.isConnected -> {
                    TileData(
                        label = appRepository.getLastConnectedDevice()?.deviceName.toString(),
                        tileState = Tile.STATE_ACTIVE,
                        subtitle = getString(CommonR.string.status_connected),
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync),
                    )
                }
                connectionState.value.isConnecting -> {
                    TileData(
                        label = (connectionState.value as ConnectionState.Connecting).device.toString(),
                        tileState = Tile.STATE_ACTIVE,
                        subtitle = getString(CommonR.string.status_connecting),
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync),
                    )
                }
                connectionState.value.isDisconnected -> {
                    val lastDevice = appRepository.getLastConnectedDevice()
                    if (lastDevice != null) {
                        TileData(
                            label = lastDevice.deviceName,
                            tileState = Tile.STATE_INACTIVE,
                            subtitle = getString(CommonR.string.status_disconnected),
                            icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync_disabled),
                        )
                    } else {
                        TileData(
                            label = getString(CommonR.string.no_device),
                            tileState = Tile.STATE_UNAVAILABLE,
                            icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync_desktop),
                        )
                    }
                }
                connectionState.value.isError -> {
                    TileData(
                        label = getString(CommonR.string.error),
                        tileState = Tile.STATE_UNAVAILABLE,
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync_problem),
                    )
                }
                else -> {
                    TileData(
                        label = "Unknown",
                        tileState = Tile.STATE_UNAVAILABLE,
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync_problem),
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