package com.castle.sefirah.presentation.main

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komu.sekia.di.AppCoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sefirah.data.repository.AppUpdateChecker
import sefirah.data.repository.ReleaseRepository
import sefirah.domain.model.PairedDevice
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val networkManager: NetworkManager,
    private val deviceManager: DeviceManager,
    private val appScope: AppCoroutineScope,
    private val appUpdateChecker: AppUpdateChecker
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val pairedDevices: StateFlow<List<PairedDevice>> = deviceManager.pairedDevices

    val newUpdate = MutableStateFlow<ReleaseRepository.Result.NewUpdate?>(null)

    val hasCheckedForUpdate = mutableStateOf(false)

    val selectedDevice: StateFlow<PairedDevice?> = combine(
        pairedDevices,
        deviceManager.selectedDeviceId
    ) { devices, selectedId ->
        devices.firstOrNull { it.deviceId == selectedId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val connectionJobs = mutableMapOf<String, Job>()

    init {
        appScope.launch {
            selectedDevice.collect { device ->
                // Clear refreshing when selected device's connection state changes to Connected or Disconnected
                if (device != null && _isRefreshing.value && 
                    (device.connectionState.isConnected || device.connectionState.isDisconnected)) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun toggleSync(syncRequest: Boolean) {
        val device = selectedDevice.value ?: return
        _isRefreshing.value = true
        
        val currentState = device.connectionState
        when {
            syncRequest && currentState.isDisconnected -> connect(device)
            syncRequest && currentState.isConnected -> {
                // Disconnect first, then reconnect
                connectionJobs.remove(device.deviceId)?.cancel()
                connectionJobs[device.deviceId] = appScope.launch(Dispatchers.IO) {
                    networkManager.disconnect(device.deviceId)
                    delay(200)
                    networkManager.connectPaired(device)
                }
            }
            !syncRequest && currentState.isConnectedOrConnecting -> {
                // Cancel any ongoing connection attempt
                connectionJobs.remove(device.deviceId)?.cancel()
                appScope.launch(Dispatchers.IO) {
                    networkManager.disconnect(device.deviceId)
                }
            }
        }
    }

    fun connect(device: PairedDevice) {
        // Cancel any existing connection attempt
        connectionJobs.remove(device.deviceId)?.cancel()
        connectionJobs[device.deviceId] = appScope.launch(Dispatchers.IO) {
            networkManager.connectPaired(device)
        }
    }

    fun selectDevice(device: PairedDevice) {
        deviceManager.selectDevice(device.deviceId)
    }

    suspend fun checkForUpdate(): ReleaseRepository.Result {
        val result = appUpdateChecker.checkForUpdate()
        if (result is ReleaseRepository.Result.NewUpdate) {
            newUpdate.value = result
        }
        return result
    }
}