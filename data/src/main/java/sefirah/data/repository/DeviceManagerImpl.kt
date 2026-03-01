package sefirah.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sefirah.database.AppRepository
import sefirah.database.model.toDomain
import sefirah.database.model.toEntity
import sefirah.domain.model.BaseRemoteDevice
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.interfaces.DeviceManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManagerImpl @Inject constructor(
    private val context: Context,
    private val appRepository: AppRepository
) : DeviceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val devicesMutex = Mutex()

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    override val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    override val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    override val selectedDeviceId: StateFlow<String?> = _selectedDeviceId.asStateFlow()

    private val _localDevice = MutableStateFlow<LocalDevice?>(null)
    override val localDeviceFlow: StateFlow<LocalDevice?> = _localDevice.asStateFlow()

    private val _pendingDeviceApproval = MutableStateFlow<PendingDeviceApproval?>(null)
    override val pendingDeviceApproval: StateFlow<PendingDeviceApproval?> = _pendingDeviceApproval.asStateFlow()

    override fun setPendingApproval(approval: PendingDeviceApproval?) {
        _pendingDeviceApproval.value = approval
    }

    override fun clearPendingApproval(deviceId: String) {
        if (_pendingDeviceApproval.value?.deviceId == deviceId) {
            _pendingDeviceApproval.value = null
        }
    }

    override val localDevice: LocalDevice
        get() = _localDevice.value ?: runBlocking(Dispatchers.IO) { ensureLocalDevice() }

    init {
        runBlocking(Dispatchers.IO) { ensureLocalDevice() }

        scope.launch {
            appRepository.getLocalDeviceFlow().collectLatest { deviceEntity ->
                _localDevice.value = deviceEntity?.toDomain()
            }
        }

        scope.launch {
            try {
                val allDevices = appRepository.getAllDevicesFlow().first()
                devicesMutex.withLock {
                    val pairedList = allDevices.map { deviceEntity ->
                        val remoteDevice = deviceEntity.toDomain()
                        remoteDevice.copy(connectionState = ConnectionState.Disconnected())
                    }
                    _pairedDevices.value = pairedList
                    
                    // Auto-select first device if none selected
                    if (_selectedDeviceId.value == null && pairedList.isNotEmpty()) {
                        _selectedDeviceId.value = pairedList.first().deviceId
                    }
                }
                Log.d(TAG, "Loaded ${_pairedDevices.value.size} paired devices from database")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading devices from database", e)
            }
        }
    }

    private suspend fun ensureLocalDevice(): LocalDevice {
        appRepository.getLocalDevice()?.toDomain()?.also { domain ->
            _localDevice.value = domain
            return domain
        }

        val created = createLocalDevice()
        _localDevice.value = created
        return created
    }

    @SuppressLint("HardwareIds")
    private suspend fun createLocalDevice(): LocalDevice {
        try {
            val deviceName = Settings.Global.getString(context.contentResolver, "device_name")
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            try {
                val file = File(context.getExternalFilesDir(null), "device_info.txt")
                file.writeText(androidId)
                Log.d(TAG, "Debug info written to: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write debug info", e)
            }
            val localDevice = LocalDevice(
                deviceId = androidId,
                deviceName = deviceName,
                model = Build.MODEL,
            )
            appRepository.addLocalDevice(localDevice.toEntity())
            return localDevice
        } catch (e: Exception) {
            Log.e("OnboardingViewModel", "Error adding device to database", e)
            throw e
        }
    }


    override suspend fun getDevice(deviceId: String): BaseRemoteDevice? {
        return devicesMutex.withLock {
            _discoveredDevices.value[deviceId] ?: _pairedDevices.value.firstOrNull { it.deviceId == deviceId }
        }
    }

    override suspend fun getDiscoveredDevice(deviceId: String): DiscoveredDevice? {
        return devicesMutex.withLock {
            _discoveredDevices.value[deviceId]
        }
    }

    override suspend fun getPairedDevice(deviceId: String): PairedDevice? {
        return try {
            devicesMutex.withLock {
                _pairedDevices.value.firstOrNull { it.deviceId == deviceId }
            } ?: run {
                val dbDevice = appRepository.getRemoteDevice(deviceId).first()?.toDomain()
                dbDevice
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired device", e)
            null
        }
    }

    override suspend fun addOrUpdateDiscoveredDevice(device: DiscoveredDevice) {
        devicesMutex.withLock {
            _discoveredDevices.update { currentDevices ->
                currentDevices + (device.deviceId to device)
            }
        }
    }

    override suspend fun removeDiscoveredDevice(deviceId: String) {
        devicesMutex.withLock {
            _discoveredDevices.update { currentDevices ->
                currentDevices - deviceId
            }
        }
    }

    override suspend fun addOrUpdatePairedDevice(device: PairedDevice) {
        devicesMutex.withLock {
            try {
                appRepository.addDevice(device.toEntity())

                _pairedDevices.update { currentDevices ->
                    if (currentDevices.any { it.deviceId == device.deviceId }) {
                        currentDevices.map { if (it.deviceId == device.deviceId) device else it }
                    } else {
                        currentDevices + device
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding/updating device", e)
            }
        }
    }

    override suspend fun removePairedDevice(deviceId: String) {
        devicesMutex.withLock {
            try {
                appRepository.removeDevice(deviceId)
                _pairedDevices.update { currentDevices ->
                    currentDevices.filter { it.deviceId != deviceId }
                }
                Log.d(TAG, "Device $deviceId removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing device", e)
            }
        }
    }

    override fun selectDevice(deviceId: String) {
        _selectedDeviceId.value = deviceId
    }

    companion object {
        private const val TAG = "DeviceManager"
    }
}