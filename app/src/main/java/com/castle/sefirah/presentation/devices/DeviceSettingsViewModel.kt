package com.castle.sefirah.presentation.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.komu.sekia.di.AppCoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardListener
import sefirah.common.util.PermissionStates
import sefirah.common.util.checkNotificationPermission
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.isNotificationListenerEnabled
import sefirah.common.util.smsPermissionGranted
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.DevicePreferences
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class DeviceSettingsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val networkManager: NetworkManager,
    private val preferencesRepository: PreferencesRepository,
    private val appScope: AppCoroutineScope,
    savedStateHandle: SavedStateHandle,
    application: Application
) : AndroidViewModel(application) {

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    private val _device = MutableStateFlow<PairedDevice?>(null)
    val device: StateFlow<PairedDevice?> = _device.asStateFlow()

    // Permission states
    private val _permissionStates = MutableStateFlow(PermissionStates())
    val permissionStates: StateFlow<PermissionStates> = _permissionStates.asStateFlow()

    val context = getApplication<Application>()

    // Device-specific settings
    val preferences: StateFlow<DevicePreferences> = preferencesRepository
        .preferenceSettings(deviceId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DevicePreferences())

    init {
        updatePermissionStates()
        viewModelScope.launch {
            deviceManager.pairedDevices.collectLatest { devices ->
                _device.value = devices.firstOrNull { it.deviceId == deviceId }
            }
        }
    }

    fun updatePermissionStates() {
        val newStates = PermissionStates(
            notificationGranted = checkNotificationPermission(context),
            batteryGranted = false, // Not needed
            locationGranted = false, // Not needed
            storageGranted = checkStoragePermission(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context, "${context.packageName}/${ClipboardListener::class.java.canonicalName}"),
            notificationListenerGranted = isNotificationListenerEnabled(context),
            smsPermissionGranted = smsPermissionGranted(context)
        )
        _permissionStates.value = newStates
    }

    fun toggleIpEnabled(address: String) {
        viewModelScope.launch {
            _device.value?.let { currentDevice ->
                val updatedIps = currentDevice.addresses.map { entry ->
                    if (entry.address == address) entry.copy(isEnabled = !entry.isEnabled)
                    else entry
                }
                val updatedDevice = currentDevice.copy(addresses = updatedIps)
                deviceManager.addOrUpdatePairedDevice(updatedDevice)
            }
        }
    }

    fun updateIpPriority(address: String, newPriority: Int) {
        viewModelScope.launch {
            _device.value?.let { currentDevice ->
                // Reorder all entries based on their current visual order
                val sortedAddresses = currentDevice.addresses.sortedBy { it.priority }.toMutableList()
                val itemIndex = sortedAddresses.indexOfFirst { it.address == address }
                
                if (itemIndex != -1 && itemIndex != newPriority) {
                    val item = sortedAddresses.removeAt(itemIndex)
                    sortedAddresses.add(newPriority.coerceIn(0, sortedAddresses.size), item)
                    
                    // Reassign priorities based on new order
                    val updatedIps = sortedAddresses.mapIndexed { index, entry ->
                        entry.copy(priority = index)
                    }
                    
                    val updatedDevice = currentDevice.copy(addresses = updatedIps)
                    deviceManager.addOrUpdatePairedDevice(updatedDevice)
                }
            }
        }
    }

    fun addCustomIp(ip: String) {
        viewModelScope.launch {
            _device.value?.let { currentDevice ->
                val trimmedIp = ip.trim()
                if (trimmedIp.isNotBlank() && currentDevice.addresses.none { it.address == trimmedIp }) {
                    val newEntry = AddressEntry(trimmedIp, isEnabled = true, priority = currentDevice.addresses.size)
                    val updatedDevice = currentDevice.copy(addresses = currentDevice.addresses + newEntry)
                    deviceManager.addOrUpdatePairedDevice(updatedDevice)
                }
            }
        }
    }

    fun removeDevice() {
        appScope.launch {
            networkManager.disconnect(deviceId)
            deviceManager.removePairedDevice(deviceId)
        }
    }

    fun removeIp(address: String) {
        viewModelScope.launch {
            _device.value?.let { currentDevice ->
                val updatedIps = currentDevice.addresses.filter { it.address != address }
                val updatedDevice = currentDevice.copy(addresses = updatedIps)
                deviceManager.addOrUpdatePairedDevice(updatedDevice)
            }
        }
    }

    fun saveClipboardSyncSettings(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveClipboardSyncSettingsForDevice(deviceId, enabled)
        }
    }

    fun saveMessageSyncSettings(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveMessageSyncSettingsForDevice(deviceId, enabled)
        }
    }

    fun saveNotificationSyncSettings(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveNotificationSyncSettingsForDevice(deviceId, enabled)
        }
    }

    fun saveImageClipboardSettings(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveImageClipboardSettingsForDevice(deviceId, enabled)
        }
    }

    fun saveMediaSessionSettings(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveMediaSessionSettingsForDevice(deviceId, enabled)
        }
    }

    fun saveRemoteStorageSettings(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveRemoteStorageSettingsForDevice(deviceId, enabled)
        }
    }

    fun hasRequestedPermission(permission: String): Flow<Boolean> {
        return preferencesRepository.hasRequestedPermission(permission)
    }

    fun savePermissionRequested(permission: String) {
        viewModelScope.launch {
            preferencesRepository.savePermissionRequested(permission)
        }
    }
}
