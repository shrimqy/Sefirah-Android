package com.castle.sefirah.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import sefirah.common.util.checkBatteryOptimization
import sefirah.common.util.checkLocationPermissions
import sefirah.common.util.checkNotificationPermission
import sefirah.common.util.checkReadMediaPermission
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.isNotificationListenerEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.domain.model.PreferencesSettings
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    application: Application
) : AndroidViewModel(application) {
    private val _preferencesSettings = MutableStateFlow<PreferencesSettings?>(null)
    val preferencesSettings: StateFlow<PreferencesSettings?> = _preferencesSettings
    val context = getApplication<Application>()

    // Add permission states
    private val _permissionStates = MutableStateFlow(PermissionStates())
    val permissionStates: StateFlow<PermissionStates> = _permissionStates

    init {
        viewModelScope.launch {
            preferencesRepository.preferenceSettings().collectLatest { settings ->
                _preferencesSettings.value = settings
                updatePermissionStates()
            }
        }
    }

    fun updatePermissionStates() {
        val previousStates = _permissionStates.value
        val newStates = PermissionStates(
            notificationGranted = checkNotificationPermission(context),
            batteryGranted = checkBatteryOptimization(context),
            locationGranted = checkLocationPermissions(context),
            storageGranted = checkStoragePermission(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context, "${context.packageName}.service.ClipboardListener") ,
            notificationListenerGranted = isNotificationListenerEnabled(context),
            readMediaGranted = checkReadMediaPermission(context)
        )
        _permissionStates.value = newStates

        handlePermissionChanges(previousStates, newStates)
    }

    private fun handlePermissionChanges(previousStates: PermissionStates, newStates: PermissionStates) {
        // Handle newly granted permissions
        handleNewlyGrantedPermissions(previousStates, newStates)

        // Handle revoked permissions
        _preferencesSettings.value?.let { settings ->
            handleRevokedPermissions(settings, newStates)
        }
    }

    private fun handleNewlyGrantedPermissions(previousStates: PermissionStates, newStates: PermissionStates) {
        if (!previousStates.accessibilityGranted && newStates.accessibilityGranted) {
            saveClipboardSyncSettings(true)
        }
        if (!previousStates.notificationListenerGranted && newStates.notificationListenerGranted) {
            saveNotificationSyncSettings(true)
        }
        if (!previousStates.readMediaGranted && newStates.readMediaGranted) {
            saveImageClipboardSettings(true)
        }
    }

    private fun handleRevokedPermissions(settings: PreferencesSettings, newStates: PermissionStates) {
        if (!newStates.accessibilityGranted && settings.clipboardSync ||
            !newStates.notificationListenerGranted && settings.notificationSync ||
            !newStates.readMediaGranted && settings.imageClipboard
        ) {
            updatePreferencesBasedOnPermissions(settings)
        }
    }

    private fun updatePreferencesBasedOnPermissions(settings: PreferencesSettings) {
        viewModelScope.launch {
            if (!_permissionStates.value.accessibilityGranted && settings.clipboardSync) {
                preferencesRepository.saveClipboardSyncSettings(false)
            }
            if (!_permissionStates.value.notificationListenerGranted && settings.notificationSync) {
                preferencesRepository.saveNotificationSyncSettings(false)
            }
            if (!_permissionStates.value.readMediaGranted && settings.imageClipboard) {
                preferencesRepository.saveImageClipboardSettings(false)
            }
        }
    }

    fun saveClipboardSyncSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveClipboardSyncSettings(boolean)
        }
    }

//    fun saveReadSensitiveNotificationsSettings(boolean: Boolean) {
//        viewModelScope.launch {
//            preferencesRepository.saveReadSensitiveNotificationsSettings(boolean)
//        }
//    }

    fun saveNotificationSyncSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveNotificationSyncSettings(boolean)
        }
    }

    fun saveMediaSessionSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveMediaSessionSettings(boolean)
        }
    }


    fun saveAutoDiscoverySettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveAutoDiscoverySettings(boolean)
        }
    }

    fun saveImageClipboardSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveImageClipboardSettings(boolean)
        }
    }

    fun updateStorageLocation(string: String) {
        viewModelScope.launch {
            preferencesRepository.updateStorageLocation(string)
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

// Add data class to hold permission states
data class PermissionStates(
    val notificationGranted: Boolean = false,
    val batteryGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val storageGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val readMediaGranted: Boolean = false,
    val readSensitiveNotificationsGranted: Boolean = false
)

