package com.castle.sefirah.presentation.network

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.NetworkEntity
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    private val _networkList = MutableStateFlow<List<NetworkEntity>>(emptyList())
    val networkList: StateFlow<List<NetworkEntity>> = _networkList

//    private val _discoveryPreference = MutableStateFlow(true)
//    val discoveryPreference: StateFlow<Boolean> = _discoveryPreference

    init {
        viewModelScope.launch {
            appRepository.getAllNetworksFlow().collectLatest { device ->
                _networkList.value = device
            }
        }

//        viewModelScope.launch {
//            preferencesRepository.readPassiveDiscoverySettings().collect { enabled ->
//                _discoveryPreference.value = enabled
//            }
//        }
    }

    fun updateNetwork(network: NetworkEntity) {
        Log.d( "NetworkViewModel","Network $network")
        viewModelScope.launch {
            appRepository.updateNetwork(network)
        }
    }

    fun deleteNetwork(network: NetworkEntity) {
        viewModelScope.launch {
            appRepository.deleteNetwork(network)
        }
    }

    fun updateNetworkPreference(preference: Boolean) {
        viewModelScope.launch {
            preferencesRepository.savePassiveDiscovery(preference)
        }
    }
}