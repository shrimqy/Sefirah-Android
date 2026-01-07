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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.database.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.ConnectionState
import sefirah.domain.repository.DeviceManager
import sefirah.domain.repository.NetworkManager
import javax.inject.Inject
import sefirah.common.R as CommonR

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class ConnectionToggleTileService : TileService() {
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var deviceManager: DeviceManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectionStateJob: kotlinx.coroutines.Job? = null
    private var connectionState: ConnectionState = ConnectionState.Disconnected()
    
    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }
    
    override fun onStartListening() {
        super.onStartListening()
        // Start observing connection state for the last connected device
        connectionStateJob = serviceScope.launch {
            combine(
                appRepository.getLastConnectedDeviceFlow(),
                deviceManager.pairedDevices
            ) { deviceEntity, paired ->
                val device = deviceEntity?.toDomain()
                if (device != null) {
                    // Get this specific device's connection state from paired devices only
                    paired.firstOrNull { it.deviceId == device.deviceId }?.connectionState 
                        ?: ConnectionState.Disconnected()
                } else {
                    ConnectionState.Disconnected()
                }
            }.collectLatest { state ->
                connectionState = state
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
            val lastDeviceEntity = appRepository.getLastConnectedDevice()
            val lastDevice = lastDeviceEntity?.toDomain()
            
            when {
                connectionState.isConnected -> {
                    // Get the device ID from the last connected device
                    val deviceId = lastDevice?.deviceId
                    if (deviceId != null) {
                        networkManager.disconnect(deviceId)
                    }
                }
                connectionState.isDisconnected && lastDevice != null -> {
                    // Get the paired device to ensure we have the latest connection info
                    val pairedDevice = deviceManager.getPairedDevice(lastDevice.deviceId)
                    if (pairedDevice != null) {
                        networkManager.connectPaired(pairedDevice)
                    }
                }
                connectionState.isDisconnected && lastDevice == null -> {
                    // No device to connect to
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
                        label = appRepository.getLastConnectedDevice()?.deviceName.toString(),
                        tileState = Tile.STATE_ACTIVE,
                        subtitle = getString(CommonR.string.status_connected),
                        icon = Icon.createWithResource(this@ConnectionToggleTileService, sefirah.presentation.R.drawable.sync),
                    )
                }

                connectionState.isDisconnected -> {
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

                connectionState.isError -> {
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