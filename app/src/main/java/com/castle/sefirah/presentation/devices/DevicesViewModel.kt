package com.castle.sefirah.presentation.devices

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val syncStatus: StateFlow<Boolean> = preferencesRepository.readSyncStatus()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _deviceDetails = MutableStateFlow<List<RemoteDevice>>(emptyList())
    private val _filteredDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val deviceDetails: StateFlow<List<RemoteDevice>> = _filteredDevices

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
                filterDevices()
            }
        }

        // Handle search query changes
        viewModelScope.launch {
            _searchQuery.collectLatest {
                filterDevices()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun filterDevices() {
        val query = _searchQuery.value
        _filteredDevices.value = if (query.isEmpty()) {
            _deviceDetails.value
        } else {
            _deviceDetails.value.filter { device ->
                device.deviceName.contains(query, ignoreCase = true) ||
                device.ipAddresses.contains(query)
            }
        }
    }
}