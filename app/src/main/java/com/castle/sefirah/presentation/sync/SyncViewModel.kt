package com.castle.sefirah.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.SyncRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.PairMessage
import sefirah.domain.repository.DeviceManager
import sefirah.domain.repository.NetworkManager
import sefirah.network.NetworkDiscovery
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val networkManager: NetworkManager,
    private val networkDiscovery: NetworkDiscovery
) : ViewModel() {

    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> = deviceManager.discoveredDevices
    private var navigationJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun pair(device: DiscoveredDevice, rootNavController: NavController) {
        // Cancel previous navigation job if any
        navigationJob?.cancel()
        
        viewModelScope.launch {
            try {
                if (device.isPairing) {
                    val updatedDevice = device.copy(isPairing = false)
                    deviceManager.addOrUpdateDiscoveredDevice(updatedDevice)
                    return@launch
                }

                val updatedDevice = device.copy(isPairing = true)
                deviceManager.addOrUpdateDiscoveredDevice(updatedDevice)

                networkManager.sendMessage(device.deviceId, PairMessage(true))

                // Watch for device to disappear from discoveredDevices or isPairing to become false
                navigationJob = viewModelScope.launch {
                    discoveredDevices.collectLatest { devices ->
                        val currentDevice = devices[device.deviceId]
                        
                        if (currentDevice == null) {
                            rootNavController.navigate(route = Graph.MainScreenGraph) {
                                popUpTo(SyncRoute.SyncScreen.route) { inclusive = true }
                            }
                            navigationJob?.cancel()
                        } else if (!currentDevice.isPairing) {
                            navigationJob?.cancel()
                        }
                    }
                }
            } catch (e: Exception) {
                // Reset pairing state on error
                val resetDevice = device.copy(isPairing = false)
                deviceManager.addOrUpdateDiscoveredDevice(resetDevice)
                navigationJob?.cancel()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            networkDiscovery.broadcastDevice()
            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.5.seconds)
            _isRefreshing.value = false
        }
    }

    fun connectFromQrCode(connectionDetails: ConnectionDetails, rootNavController: NavController) {
        viewModelScope.launch {
            try {
                // Connect to the device
                withContext(Dispatchers.IO) {
                    networkManager.connectTo(connectionDetails)
                }

                val device = discoveredDevices.value[connectionDetails.deviceId] ?: return@launch
                pair(device, rootNavController)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting from QR code", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        navigationJob?.cancel()
    }

    companion object {
        private const val TAG = "SyncViewModel"
    }
}