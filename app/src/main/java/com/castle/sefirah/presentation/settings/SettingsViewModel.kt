package com.castle.sefirah.presentation.settings

import android.annotation.SuppressLint
import android.app.Application
import android.app.WallpaperManager
import android.provider.Settings.Global
import android.provider.Settings.Secure
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardListener
import sefirah.common.util.PermissionStates
import sefirah.data.repository.AppRepository
import sefirah.database.model.toEntity
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.PreferencesSettings
import sefirah.domain.repository.PreferencesRepository
import sefirah.network.util.ECDHHelper
import sefirah.presentation.util.drawableToBase64Compressed
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appRepository: AppRepository,
    application: Application
) : AndroidViewModel(application) {
    private val _preferencesSettings = MutableStateFlow<PreferencesSettings?>(null)
    val preferencesSettings: StateFlow<PreferencesSettings?> = _preferencesSettings

    var appEntryValue by mutableStateOf(false)

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

        viewModelScope.launch {
            appEntryValue = preferencesRepository.readAppEntry()
        }
    }

    fun updatePermissionStates() {
        val previousStates = _permissionStates.value
        val newStates = PermissionStates(
            notificationGranted = checkNotificationPermission(context),
            batteryGranted = checkBatteryOptimization(context),
            locationGranted = checkLocationPermissions(context),
            storageGranted = checkStoragePermission(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context, "${context.packageName}/${ClipboardListener::class.java.canonicalName}") ,
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
        if (!previousStates.accessibilityGranted && newStates.accessibilityGranted)
            saveClipboardSyncSettings(true)

        if (!previousStates.notificationListenerGranted && newStates.notificationListenerGranted)
            saveNotificationSyncSettings(true)

        if (!previousStates.readMediaGranted && newStates.readMediaGranted)
            saveImageClipboardSettings(true)

        if (!previousStates.storageGranted && newStates.storageGranted)
            saveRemoteStorageSettings(true)
    }

    private fun handleRevokedPermissions(settings: PreferencesSettings, newStates: PermissionStates) {
        if (!newStates.accessibilityGranted && settings.clipboardSync ||
            !newStates.notificationListenerGranted && settings.notificationSync ||
            !newStates.readMediaGranted && settings.imageClipboard ||
            !newStates.storageGranted && settings.remoteStorage
        ) {
            updatePreferencesBasedOnPermissions(settings)
        }
    }

    private fun updatePreferencesBasedOnPermissions(settings: PreferencesSettings) {
        viewModelScope.launch {
            if (!_permissionStates.value.accessibilityGranted && settings.clipboardSync)
                preferencesRepository.saveClipboardSyncSettings(false)

            if (!_permissionStates.value.notificationListenerGranted && settings.notificationSync)
                preferencesRepository.saveNotificationSyncSettings(false)

            if (!_permissionStates.value.readMediaGranted && settings.imageClipboard)
                preferencesRepository.saveImageClipboardSettings(false)

            if (!_permissionStates.value.storageGranted && settings.remoteStorage)
                preferencesRepository.saveRemoteStorageSettings(false)

        }
    }

    fun saveAppEntry() {
        viewModelScope.launch {
            updateLocalDeviceData()
            preferencesRepository.saveAppEntry()
        }
    }

    @SuppressLint("HardwareIds")
    fun updateLocalDeviceData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (publicKey, privateKey) = ECDHHelper.generateKeys()
                val deviceName = Global.getString(getApplication<Application>().contentResolver, "device_name")
                val androidId = Secure.getString(getApplication<Application>().contentResolver, Secure.ANDROID_ID)

                val wallpaperBase64 = try {
                    val wallpaperManager = WallpaperManager.getInstance(getApplication())
                    wallpaperManager.drawable?.let { drawable ->
                        drawableToBase64Compressed(drawable)
                    }
                } catch (e: SecurityException) {
                    Log.w("OnboardingViewModel", "Unable to access wallpaper", e)
                    null
                }

                appRepository.addLocalDevice(
                    LocalDevice(
                        deviceId = androidId,
                        deviceName = deviceName,
                        publicKey = publicKey,
                        privateKey = privateKey,
                    ).toEntity()
                )
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error adding device to database", e)
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

    fun saveRemoteStorageSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveRemoteStorageSettings(boolean)
        }
    }
}
