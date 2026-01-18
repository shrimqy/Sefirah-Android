package sefirah.domain.interfaces

import kotlinx.coroutines.flow.StateFlow
import sefirah.domain.model.BaseRemoteDevice
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.PairedDevice

interface DeviceManager {
    val pairedDevices: StateFlow<List<PairedDevice>>
    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>>
    val selectedDeviceId: StateFlow<String?>
    val localDevice: LocalDevice
    val localDeviceFlow: StateFlow<LocalDevice?>
    fun selectDevice(deviceId: String)
    
    suspend fun getDevice(deviceId: String): BaseRemoteDevice?
    suspend fun getDiscoveredDevice(deviceId: String): DiscoveredDevice?
    suspend fun getPairedDevice(deviceId: String): PairedDevice?

    suspend fun addOrUpdateDiscoveredDevice(device: DiscoveredDevice)
    suspend fun removeDiscoveredDevice(deviceId: String)
    
    suspend fun addOrUpdatePairedDevice(device: PairedDevice)
    suspend fun removePairedDevice(deviceId: String)
}

