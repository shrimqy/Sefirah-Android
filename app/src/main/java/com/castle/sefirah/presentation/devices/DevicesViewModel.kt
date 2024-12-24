package com.castle.sefirah.presentation.devices

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.RemoteDevice
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    appRepository: AppRepository,
    application: Application
) : AndroidViewModel(application) {

    val syncStatus: StateFlow<Boolean> = preferencesRepository.readSyncStatus()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _deviceDetails = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val deviceDetails: StateFlow<List<RemoteDevice>> = _deviceDetails

    private val _lastConnected = MutableStateFlow<String?>(null)
    val lastConnected: StateFlow<String?> = _lastConnected

    init {
        viewModelScope.launch {
            preferencesRepository.readLastConnected().collectLatest { lastConnectedValue ->
                _lastConnected.value = lastConnectedValue
            }
        }

        viewModelScope.launch {
            appRepository.getAllDevicesFlow().collectLatest { devices ->
                _deviceDetails.value = devices.toDomain()
                Log.d("DevicesViewModel", "Collected Devices: $devices")
            }
        }
    }
}