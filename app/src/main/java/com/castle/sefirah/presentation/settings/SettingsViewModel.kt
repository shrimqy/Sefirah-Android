package com.castle.sefirah.presentation.settings

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings.Global
import android.provider.Settings.Secure
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import sefirah.common.util.checkBatteryOptimization
import sefirah.common.util.checkLocationPermissions
import sefirah.common.util.checkNotificationPermission
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.isNotificationListenerEnabled
import sefirah.common.util.smsPermissionGranted
import sefirah.data.repository.AppRepository
import sefirah.database.model.toDomain
import sefirah.database.model.toEntity
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.PreferencesSettings
import sefirah.domain.repository.PreferencesRepository
import sefirah.network.util.ECDHHelper
import java.io.File
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

    private val _localDevice = MutableStateFlow<LocalDevice?>(null)
    val localDevice: StateFlow<LocalDevice?> = _localDevice

    var appEntryValue by mutableStateOf(false)

    val context = getApplication<Application>()

    // Add permission states
    private val _permissionStates = MutableStateFlow(PermissionStates())
    val permissionStates: StateFlow<PermissionStates> = _permissionStates


    init {
        updatePermissionStates()
        // set up the preference collection with a proper synchronization mechanism
        viewModelScope.launch {
            preferencesRepository.preferenceSettings().collectLatest { settings ->
                _preferencesSettings.value = settings
            }
        }
        
        viewModelScope.launch {
            appEntryValue = preferencesRepository.readAppEntry()
            // Subscribe to the flow of local device changes
            appRepository.getLocalDeviceFlow().collectLatest { device ->
                _localDevice.value = device?.toDomain()
            }
        }
    }

    fun updateDeviceName(newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get current local device
                val currentDevice = _localDevice.value ?: return@launch
                appRepository.updateLocalDeviceName(currentDevice.deviceId, newName)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating device name", e)
            }
        }
    }

    fun updatePermissionStates() {
        val newStates = PermissionStates(
            notificationGranted = checkNotificationPermission(context),
            batteryGranted = checkBatteryOptimization(context),
            locationGranted = checkLocationPermissions(context),
            storageGranted = checkStoragePermission(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context, "${context.packageName}/${ClipboardListener::class.java.canonicalName}"),
            notificationListenerGranted = isNotificationListenerEnabled(context),
            smsPermissionGranted = smsPermissionGranted(context)
        )
        _permissionStates.value = newStates
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

                appRepository.addLocalDevice(
                    LocalDevice(
                        deviceId = androidId,
                        deviceName = deviceName,
                        publicKey = publicKey,
                        privateKey = privateKey,
                    ).toEntity()
                )

                try {
                    val file = File(context.getExternalFilesDir(null), "device_info.txt")
                    file.writeText(androidId)
                    Log.d(TAG, "Debug info written to: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write debug info", e)
                }

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

    fun saveMessageSyncSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveMessageSyncSettings(boolean)
        }
    }

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
            Log.d(TAG, "Saving remoteStorage setting: $boolean")
            preferencesRepository.saveRemoteStorageSettings(boolean)
        }
    }
}
