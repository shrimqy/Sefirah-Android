
package com.castle.sefirah.presentation.devices.deviceDetails

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.database.AppRepository
import sefirah.database.model.NetworkEntity
import sefirah.database.model.RemoteDeviceEntity
import sefirah.network.NetworkService
import javax.inject.Inject

@HiltViewModel
class EditDeviceViewModel @Inject constructor(
    private val appRepository: AppRepository,
    savedStateHandle: SavedStateHandle,
    application: Application
) : AndroidViewModel(application) {

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])

    private val _device = MutableStateFlow<RemoteDeviceEntity?>(null)
    val device = _device.asStateFlow()

    private val _associatedNetworks = MutableStateFlow<List<NetworkEntity>>(emptyList())
    val associatedNetworks = _associatedNetworks.asStateFlow()

    init {
        viewModelScope.launch {
            appRepository.getRemoteDevice(deviceId).collectLatest {
                _device.value = it
            }
        }
        viewModelScope.launch {
            appRepository.getNetworksForDevice(deviceId).collectLatest {
                _associatedNetworks.value = it
            }
        }
    }

    fun setPreferredIp(ip: String) {
        viewModelScope.launch {
            _device.value?.let { device ->
                appRepository.updatePreferredIp(deviceId = device.deviceId, preferredIp = ip)
            }
        }
    }

    fun addCustomIp(ip: String) {
        viewModelScope.launch {
            _device.value?.let { device ->
                val updatedIps = device.ipAddresses.toMutableList().apply {
                    add(ip)
                }
                appRepository.updateIpAddresses(
                    deviceId = device.deviceId,
                    ipAddresses = updatedIps
                )
            }
        }
    }

    fun removeDevice() {
        viewModelScope.launch {
            appRepository.removeDevice(deviceId)
            val intent = Intent(getApplication(), NetworkService::class.java).apply {
                this.action = NetworkService.Companion.Actions.STOP.name
            }
            getApplication<Application>().startService(intent)
        }
    }

    fun removeNetworkFromDevice(ssid: String) {
        viewModelScope.launch {
            appRepository.removeNetworkFromDevice(deviceId, ssid)
        }
    }

    fun removeIp(ip: String) {
        viewModelScope.launch {
            _device.value?.let { device ->
                val updatedIps = device.ipAddresses.toMutableList().apply {
                    remove(ip)
                }
                appRepository.updateIpAddresses(deviceId = device.deviceId, ipAddresses = updatedIps)
            }
        }
    }
}
