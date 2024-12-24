package com.castle.sefirah.presentation.sync

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketMessage
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.Actions
import sefirah.network.NsdService
import sefirah.network.util.ECDHHelper.deriveSharedSecret
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    private val nsdService: NsdService,
    private val appRepository: AppRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<SocketMessage>>(emptyList())
    val messages: StateFlow<List<SocketMessage>> get() = _messages

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> get() = _isConnected

    // Capture the stateflow from the service as the data updates
    val services: StateFlow<List<RemoteDevice>> = nsdService.services
    private lateinit var localDevice: LocalDevice
    init {
        // Starting Nsd Discovery for mDNS services at start
        viewModelScope.launch {
            nsdService.startDiscovery()
            localDevice = appRepository.getLocalDevice().toDomain()
            nsdService.advertiseService(localDevice.publicKey)
            delay(1.seconds)
            if (services.value.isEmpty()) {
                Toast.makeText(application.applicationContext, "Make sure you're connected to the same network as your PC", Toast.LENGTH_LONG).show()
            }
        }
    }



    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun authenticate(context: Context, remoteDevice: RemoteDevice, hashedSecret: String) {
        viewModelScope.launch {
            try {
                remoteDevice.hashedSecret = hashedSecret
                val intent = Intent(context, NetworkService::class.java).apply {
                    action = Actions.START.name
                    putExtra(NetworkService.REMOTE_INFO, remoteDevice)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to service: ${e.message}", e)
            }
        }
    }

    fun findServices() {
        viewModelScope.launch {
            _isRefreshing.value = true
            nsdService.startDiscovery()
            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.5.seconds)
            _isRefreshing.value = false
        }
    }

    fun deriveSharedSecretCode(publicKey: String): ByteArray {
        return deriveSharedSecret(localDevice.privateKey, publicKey)
    }

    private fun stopDiscovery() {
        nsdService.stopDiscovery()
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val PORT = 5149
    }
}