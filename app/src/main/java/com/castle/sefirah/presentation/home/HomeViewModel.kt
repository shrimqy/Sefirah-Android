package com.castle.sefirah.presentation.home

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.komu.sekia.di.AppCoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.LocalDevice
import sefirah.domain.model.MediaAction
import sefirah.domain.model.PlaybackData
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketMessage
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PlaybackRepository
import sefirah.domain.repository.PreferencesRepository
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.Actions
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    playbackRepository: PlaybackRepository,
    private val networkManager: NetworkManager,
    private val appScope: AppCoroutineScope,
    private val appRepository: AppRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _syncStatus = MutableStateFlow(false)
    val syncStatus: StateFlow<Boolean> = _syncStatus

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _deviceDetails = MutableStateFlow<RemoteDevice?>(null)
    val deviceDetails: StateFlow<RemoteDevice?> = _deviceDetails

    val playbackData: StateFlow<PlaybackData?> = playbackRepository.readPlaybackData()

    init {
        appScope.launch {
            appRepository.getLastConnectedDeviceFlow().collectLatest { device ->
                if (device != null) {
                    Log.d("HomeViewModel", "Device found: ${device.deviceName}")
                    _deviceDetails.value = device.toDomain()

                    if (!syncStatus.value) {
                        toggleSync(true)
                    }
                } else {
                    _deviceDetails.value = null
                }
            }
        }

        appScope.launch {
            networkManager.isConnected.collectLatest { state ->
                _syncStatus.value = state
            }
        }
    }

    fun toggleSync(syncRequest: Boolean) {
        appScope.launch {
            _isRefreshing.value = true
            if (deviceDetails.value != null) {
                // Proceed based on current status
                if (syncRequest and !syncStatus.value){
                    val intent = Intent(getApplication(), NetworkService::class.java).apply {
                        action = Actions.START.name
                        putExtra(NetworkService.REMOTE_INFO, _deviceDetails.value)
                    }
                    getApplication<Application>().startService(intent)
                } else if (syncRequest and syncStatus.value) {
                    var intent = Intent(getApplication(), NetworkService::class.java).apply {
                        action = Actions.STOP.name
                    }
                    getApplication<Application>().startService(intent)
                    delay(100)
                    intent = Intent(getApplication(), NetworkService::class.java).apply {
                        action = Actions.START.name
                        putExtra(NetworkService.REMOTE_INFO, _deviceDetails.value)
                    }
                    getApplication<Application>().startService(intent)
                } else if (!syncRequest and syncStatus.value){
                    val intent = Intent(getApplication(), NetworkService::class.java).apply {
                        action = Actions.STOP.name
                    }
                    getApplication<Application>().startService(intent)
                }
            }
            delay(1.seconds)
            _isRefreshing.value = false
        }
    }

    // Handle play/pause, next, previous actions
    fun onPlayPause() {
        val action: MediaAction = if (playbackData.value?.isPlaying == true) MediaAction.PAUSE else MediaAction.RESUME
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(appName = playbackData.value!!.appName, mediaAction = action))
        }
    }

    fun onNext() {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(appName = playbackData.value!!.appName, mediaAction = MediaAction.NEXT_QUEUE))
        }
    }

    fun onPrevious() {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(appName = playbackData.value!!.appName, mediaAction = MediaAction.PREV_QUEUE))
        }
    }

    fun onVolumeChange(volume: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(volume = volume.toFloat(), appName = playbackData.value?.appName, mediaAction = MediaAction.VOLUME))
        }
    }

    private fun sendPlaybackData(playbackData: PlaybackData, mediaAction: MediaAction) {
        playbackData.mediaAction = mediaAction
        CoroutineScope(Dispatchers.IO).launch {
            sendMessage(playbackData)
        }
        Log.d("MediaSession", "Action received: $mediaAction" + playbackData.trackTitle)
    }

    private suspend fun sendMessage(message: SocketMessage) {
        networkManager.sendMessage(message)
    }
}
