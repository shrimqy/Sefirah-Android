package com.castle.sefirah.presentation.main

import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.komu.sekia.di.AppCoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.data.repository.AppUpdateChecker
import sefirah.data.repository.ReleaseRepository
import sefirah.database.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.RemoteDevice
import sefirah.domain.repository.NetworkManager
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.Actions
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val networkManager: NetworkManager,
    private val appScope: AppCoroutineScope,
    private val appRepository: AppRepository,
    private val appUpdateChecker: AppUpdateChecker,
    application: Application
) : AndroidViewModel(application) {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _deviceDetails = MutableStateFlow<RemoteDevice?>(null)
    val deviceDetails: StateFlow<RemoteDevice?> = _deviceDetails

    private val _newUpdate = MutableStateFlow<ReleaseRepository.Result.NewUpdate?>(null)
    val newUpdate: StateFlow<ReleaseRepository.Result.NewUpdate?> = _newUpdate

    val hasCheckedForUpdate = mutableStateOf(false)

    init {
        appScope.launch {
            networkManager.connectionState.collectLatest { state ->
                _connectionState.value = state
            }
        }

        appScope.launch {
            appRepository.getLastConnectedDeviceFlow().first()?.let {
                if (_connectionState.value == ConnectionState.Disconnected()) {
                    toggleSync(true)
                }
            }
        }

        appScope.launch {
            appRepository.getLastConnectedDeviceFlow().collectLatest { device ->
                if (device != null) {
                    _deviceDetails.value = device.toDomain()
                } else {
                    _deviceDetails.value = null
                }
            }
        }
    }

    fun toggleSync(syncRequest: Boolean) {
       appScope.launch {
           Log.d("Viewmodel", "sync request is $syncRequest and connection state is ${connectionState.value}")
            _deviceDetails.value?.let { device ->
                _isRefreshing.value = true
                when {
                    syncRequest && _connectionState.value.isDisconnected -> {
                        startService(Actions.START, device)
                    }
                    syncRequest && _connectionState.value.isConnected -> {
                        _connectionState.value = ConnectionState.Disconnected()
                        startService(Actions.STOP)
                        delay(200)
                        startService(Actions.START, device)
                    }
                    !syncRequest && (_connectionState.value.isConnectedOrConnecting) -> {
                        _connectionState.value.isDisconnected
                        startService(Actions.STOP, device)
                    }
                }
            } ?: run {
                _isRefreshing.value = false  // Ensure refreshing stops if no device
            }
        }
    }

    fun connect(device: RemoteDevice) {
        startService(Actions.START, device)
    }

    private var connectionStateJob: Job? = null

    private fun startService(action: Actions, device: RemoteDevice? = null) {
        val intent = Intent(getApplication(), NetworkService::class.java).apply {
            this.action = action.name
            device?.let {
                // Create a copy without the avatar to avoid size limit issues when passing intent
                val remoteInfo = it.copy(avatar = null)
                putExtra(NetworkService.REMOTE_INFO, remoteInfo)
            }
        }

        try {
            getApplication<Application>().startService(intent)
        } catch (e: Exception) {
            _isRefreshing.value = false
            return
        }
        try {
            connectionStateJob = appScope.launch {
                connectionState.collect { state ->
                    when {
                        state.isConnected -> {
                            _isRefreshing.value = false
                            connectionStateJob?.cancel()
                        }
                        state.isForcedDisconnect -> {
                            _isRefreshing.value = false
                            connectionStateJob?.cancel()
                        }
                        state.isConnecting -> {
                            if (!_isRefreshing.value) {
                                _isRefreshing.value = true
                            }
                        }
                        state.isError -> {
                            _isRefreshing.value = false
                            _connectionState.value = ConnectionState.Disconnected()
                            withContext(Dispatchers.Main.immediate) {
                                Toast.makeText(
                                    getApplication(),
                                    (state as ConnectionState.Error).message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            connectionStateJob?.cancel()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ConnectionViewModel", "Job cancelled probably", e)
            _isRefreshing.value = false
        }
    }

    suspend fun checkForUpdate(): ReleaseRepository.Result {
        val result = appUpdateChecker.checkForUpdate()
        if (result is ReleaseRepository.Result.NewUpdate) {
            _newUpdate.value = result
        }
        return result
    }
}