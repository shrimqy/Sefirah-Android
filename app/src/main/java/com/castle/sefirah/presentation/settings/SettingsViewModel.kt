package com.castle.sefirah.presentation.settings

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardAccessibilityService
import sefirah.worker.ShizukuHelper
import sefirah.common.util.PermissionStates
import sefirah.common.util.checkBatteryOptimization
import sefirah.common.util.checkLocationPermissions
import sefirah.common.util.checkNotificationPermission
import sefirah.common.util.checkStoragePermission
import sefirah.common.util.contactsPermissionGranted
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.isCallLogsPermissionGranted
import sefirah.common.util.isNotificationListenerEnabled
import sefirah.common.util.nearbyDevicesPermissionGranted
import sefirah.common.util.phoneStatePermissionGranted
import sefirah.common.util.smsPermissionGranted
import sefirah.database.AppRepository
import sefirah.database.model.NetworkEntity
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.LocalDevice
import sefirah.network.NetworkDiscovery
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appRepository: AppRepository,
    private val networkManager: NetworkManager,
    private val presentationPublisher: sefirah.status.DevicePresentationPublisher,
    networkDiscovery: NetworkDiscovery,
    deviceManager: DeviceManager,
    application: Application
) : AndroidViewModel(application) {
    var presentation by mutableStateOf(sefirah.domain.model.DevicePresentation())
        private set
    var presentationPreview by mutableStateOf<sefirah.domain.model.DevicePresentation?>(null)
        private set
    private suspend fun refreshPreview() { presentationPreview = presentationPublisher.metadata(null, true) }
    var shareWallpaper by mutableStateOf(false)
        private set
    init {
        viewModelScope.launch {
            presentation = presentationPublisher.localSettings()
            shareWallpaper = preferencesRepository.readPresentation("share").first() == "true"
            refreshPreview()
        }
    }
    fun updatePresentation(value: sefirah.domain.model.DevicePresentation) {
        if (!value.isValid()) return
        presentation = value
        viewModelScope.launch {
            preferencesRepository.savePresentation("local", kotlinx.serialization.json.Json.encodeToString(sefirah.domain.model.DevicePresentation.serializer(), value.metadata()))
            refreshPreview()
            presentationPublisher.publishAll()
        }
    }
    fun shareWallpaper(enabled: Boolean) {
        shareWallpaper = enabled
        viewModelScope.launch {
            preferencesRepository.savePresentation("share", enabled.toString())
            refreshPreview()
            presentationPublisher.publishAll()
        }
    }
    fun selectPresentationImage(uri: android.net.Uri) { viewModelScope.launch(Dispatchers.IO) { presentationPublisher.saveImage(uri); refreshPreview() } }
    fun useSystemWallpaper() { presentationPublisher.useWallpaper(); viewModelScope.launch { refreshPreview(); presentationPublisher.publishAll() } }

    val localDevice: StateFlow<LocalDevice?> = deviceManager.localDeviceFlow

    val networkList: StateFlow<List<NetworkEntity>> = appRepository
        .getAllNetworksFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trustAllNetworks: StateFlow<Boolean> = preferencesRepository
        .readTrustAllNetworks()
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val showActionLabels: StateFlow<Boolean> = preferencesRepository
        .readShowActionLabels()
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val clipboardWorkerEnabled: StateFlow<Boolean> = preferencesRepository
        .readClipboardWorkerEnabled()
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    private val _storageLocation = MutableStateFlow("")
    val storageLocation: StateFlow<String> = _storageLocation

    private val _recycleBinLocation = MutableStateFlow("")
    val recycleBinLocation: StateFlow<String> = _recycleBinLocation

    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language

    var appEntry by mutableStateOf(false)

    val context = getApplication<Application>()

    private val _permissionStates = MutableStateFlow(PermissionStates())
    val permissionStates: StateFlow<PermissionStates> = _permissionStates


    init {
        updatePermissionStates()
        
        viewModelScope.launch {
            preferencesRepository.getStorageLocation().collectLatest { location ->
                _storageLocation.value = location
            }
        }

        viewModelScope.launch {
            preferencesRepository.getRecycleBinLocation().collectLatest { location ->
                _recycleBinLocation.value = location
            }
        }
        
        viewModelScope.launch {
            preferencesRepository.readLanguage().collectLatest { lang ->
                _language.value = lang
            }
        }

        viewModelScope.launch {
            appEntry = preferencesRepository.readAppEntry()
        }
    }

    fun updateDeviceName(newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentDevice = localDevice.value ?: return@launch
                appRepository.updateLocalDeviceName(currentDevice.deviceId, newName)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating device name", e)
            }
        }
    }

    fun updatePermissionStates() {
        viewModelScope.launch {
            val clearPermission: (String) -> Unit = { permission ->
                clearPermissionRequested(permission)
            }
            
            val notificationGranted = checkNotificationPermission(context, clearPermission)
            val locationGranted = checkLocationPermissions(context, clearPermission)
            val nearbyDevicesGranted = nearbyDevicesPermissionGranted(context, clearPermission)
            val storageGranted = checkStoragePermission(context, clearPermission)
            val smsGranted = smsPermissionGranted(context, clearPermission)
            val contactsGranted = contactsPermissionGranted(context, clearPermission)
            val phoneStateGranted = phoneStatePermissionGranted(context, clearPermission)
            val callLogsGranted = isCallLogsPermissionGranted(context)

            _permissionStates.value = PermissionStates(
                notificationGranted = notificationGranted,
                batteryGranted = checkBatteryOptimization(context),
                locationGranted = locationGranted,
                nearbyDevicesGranted = nearbyDevicesGranted,
                overlayGranted = Settings.canDrawOverlays(context),
                storageGranted = storageGranted,
                accessibilityGranted = isAccessibilityServiceEnabled(context, "${context.packageName}/${ClipboardAccessibilityService::class.java.canonicalName}"),
                shizukuGranted = ShizukuHelper.isAuthorized(),
                notificationListenerGranted = isNotificationListenerEnabled(context),
                smsPermissionGranted = smsGranted,
                contactsGranted = contactsGranted,
                phoneStateGranted = phoneStateGranted,
                callLogsGranted = callLogsGranted
            )
        }
    }

    fun stopService() {
        networkManager.stopService()
    }

    fun saveAppEntry() {
        viewModelScope.launch {
            preferencesRepository.saveAppEntry()
        }
    }

    fun updateStorageLocation(string: String) {
        viewModelScope.launch {
            preferencesRepository.updateStorageLocation(string)
        }
    }

    fun updateRecycleBinLocation(string: String) {
        viewModelScope.launch {
            preferencesRepository.updateRecycleBinLocation(string)
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

    fun clearPermissionRequested(permission: String) {
        viewModelScope.launch {
            preferencesRepository.clearPermissionRequested(permission)
        }
    }

    fun deleteNetwork(network: NetworkEntity) {
        viewModelScope.launch {
            appRepository.deleteNetwork(network)
        }
    }

    fun saveTrustAllNetworks(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveTrustAllNetworks(enabled)
        }
    }

    fun saveShowActionLabels(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveShowActionLabels(enabled)
        }
    }

    fun saveClipboardWorkerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveClipboardWorkerEnabled(enabled)
        }
    }

    /**
     * StateFlow for the current WiFi network SSID.
     * Returns null if no network is connected or location permissions are not granted.
     */
    val currentWifiSsid: StateFlow<String?> = networkDiscovery.currentWifiSsid

    /**
     * Adds the current WiFi network as a trusted network.
     * Returns true if the network was added successfully, false otherwise.
     */
    fun addCurrentNetworkAsTrusted(): Boolean {
        val ssid = currentWifiSsid.value ?: return false
        
        // Check if network already exists and add if not
        viewModelScope.launch(Dispatchers.IO) {
            val existingNetwork = appRepository.getNetwork(ssid)
            if (existingNetwork == null) {
                appRepository.addNetwork(NetworkEntity(ssid = ssid))
            }
        }
        return true
    }
}
