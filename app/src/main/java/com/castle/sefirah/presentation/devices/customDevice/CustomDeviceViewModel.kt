package com.castle.sefirah.presentation.devices.customDevice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.data.repository.AppRepository
import sefirah.database.model.CustomIpEntity
import sefirah.network.NetworkDiscovery
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import java.net.InetAddress

@HiltViewModel
class CustomDeviceViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val networkDiscovery: NetworkDiscovery
) : ViewModel() {
    private val _customIps = MutableStateFlow<List<String>>(emptyList())
    val customIps: StateFlow<List<String>> = _customIps

    private val _customIpsStatus = MutableStateFlow<Map<String, PingResult>>(emptyMap())
    val customIpsStatus: StateFlow<Map<String, PingResult>> = _customIpsStatus

    init {
        viewModelScope.launch {
            appRepository.getAllCustomIpFlow().collect { ips ->
                _customIps.value = ips
                // Ping all IPs whenever the list changes
                pingAllDevices(ips)
            }
        }
    }

    private fun pingAllDevices(ips: List<String>) {
        viewModelScope.launch {
            val results = ips.associateWith { ip ->
                pingDevice(ip)
            }
            _customIpsStatus.value = results
        }
    }

    fun addCustomIp(ip: String) {
        if (ip.isBlank() || _customIps.value.contains(ip)) return
        
        viewModelScope.launch {
            appRepository.addCustomIp(CustomIpEntity(ip))
        }
    }

    fun deleteCustomIp(ip: String) {
        viewModelScope.launch {
            appRepository.deleteCustomIp(CustomIpEntity(ip))
        }
    }

    data class PingResult(
        val isReachable: Boolean,
        val responseTime: Long? = null
    )

    private suspend fun pingDevice(ip: String, timeout: Int = 2000): PingResult = 
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val isReachable = InetAddress.getByName(ip).isReachable(timeout)
                val endTime = System.currentTimeMillis()
                
                PingResult(
                    isReachable = isReachable,
                    responseTime = if (isReachable) endTime - startTime else null
                )
            } catch (e: Exception) {
                Log.e("CustomDeviceViewModel", "Error pinging device at $ip", e)
                PingResult(isReachable = false)
            }
        }
}