package com.castle.sefirah.presentation.sync

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.SyncRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.RemoteDevice
import sefirah.domain.repository.NetworkManager
import sefirah.network.DiscoveredDevice
import sefirah.network.NetworkDiscovery
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
    private val appRepository: AppRepository,
    private val networkManager: NetworkManager,
    private val networkDiscovery: NetworkDiscovery
) : ViewModel() {

    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = networkDiscovery.discoveredDevices
    private lateinit var localDevice: LocalDevice
    init {
        // Starting Nsd Discovery for mDNS services at start
        viewModelScope.launch {
            nsdService.startDiscovery()
            localDevice = appRepository.getLocalDevice().toDomain()
            networkDiscovery.startDiscovery()
            delay(1.seconds)
            if (discoveredDevices.value.isEmpty()) {
                Toast.makeText(application.applicationContext, "Make sure you're connected to the same network as your PC", Toast.LENGTH_LONG).show()
            }
        }

        viewModelScope.launch {
            networkManager.connectionState.collectLatest { state ->
                connectionState.value = state
            }
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var connectionStateJob: Job? = null

    fun authenticate(context: Context, remoteDevice: RemoteDevice, rootNavController: NavController) {
        // Create a copy with preferred address (might be null)
        val finalDevice = remoteDevice.copy(
            prefAddress = remoteDevice.prefAddress?.takeIf { it.isNotBlank() }
        )

        connectionStateJob = viewModelScope.launch {
            try {
                val intent = Intent(context, NetworkService::class.java).apply {
                    action = Actions.START.name
                    putExtra(NetworkService.REMOTE_INFO, finalDevice)
                }
                context.startService(intent)
                delay(200)
                // Monitor connection state changes until Connected or Disconnected
                connectionState.collect { state ->
                    when (state) {
                        ConnectionState.Connected -> {
                            _isRefreshing.value = false
                            networkDiscovery.stopDiscovery()
                            rootNavController.navigate(route = Graph.MainScreenGraph) {
                                popUpTo(SyncRoute.SyncScreen.route) { inclusive = true }
                            }
                            connectionStateJob?.cancel() // Stop collecting after navigation
                        }
                        is ConnectionState.Disconnected -> {
                            _isRefreshing.value = false
                            connectionStateJob?.cancel() // Stop collecting after failure
                        }
                        ConnectionState.Connecting -> {
                            _isRefreshing.value = true
                        }
                        is ConnectionState.Error -> {
                            _isRefreshing.value = false
//                            Toast.makeText(
//                                context,
//                                state.message,
//                                Toast.LENGTH_LONG
//                            ).show()
                            connectionStateJob?.cancel()
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _isRefreshing.value = false
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


    override fun onCleared() {
        super.onCleared()
        connectionStateJob?.cancel()
        networkDiscovery.stopDiscovery()
        nsdService.stopAdvertisingService()
        nsdService.stopDiscovery()
    }

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val PORT = 5149
    }
}