package com.castle.sefirah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val networkManager: NetworkManager,
    private val preferencesRepository: PreferencesRepository,
): ViewModel() {
    
    val startDestination: String = runBlocking(Dispatchers.IO) {
        val hasCompletedOnboarding = preferencesRepository.readAppEntry()
        if (hasCompletedOnboarding) {
            Graph.MainScreenGraph
        } else {
            OnboardingRoute.OnboardingScreen.route
        }
    }
    
    val pendingDeviceApproval: StateFlow<PendingDeviceApproval?> = networkManager.pendingDeviceApproval

    fun approveDevice(deviceId: String) {
        viewModelScope.launch {
            networkManager.approveDeviceConnection(deviceId)
        }
    }

    fun rejectDevice(deviceId: String) {
        viewModelScope.launch {
            networkManager.rejectDeviceConnection(deviceId)
        }
    }
}