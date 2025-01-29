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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.toDomain
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.MediaAction
import sefirah.domain.model.PlaybackData
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.SocketMessage
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PlaybackRepository
import sefirah.domain.repository.PreferencesRepository
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.Actions
import sefirah.network.NetworkService.Companion.TAG
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    playbackRepository: PlaybackRepository,
    private val networkManager: NetworkManager,
    private val appScope: AppCoroutineScope,
    private val appRepository: AppRepository,
    application: Application
) : AndroidViewModel(application) {

    val playbackData: StateFlow<PlaybackData?> = playbackRepository.readPlaybackData()

    // Handle play/pause, next, previous actions
    fun onPlayPause() {
        val action: MediaAction = if (playbackData.value?.isPlaying == true) MediaAction.Pause else MediaAction.Resume
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(appName = playbackData.value!!.appName, mediaAction = action))
        }
    }

    fun onNext() {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(appName = playbackData.value!!.appName, mediaAction = MediaAction.NextQueue))
        }
    }

    fun onPrevious() {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(appName = playbackData.value!!.appName, mediaAction = MediaAction.PrevQueue))
        }
    }

    fun onVolumeChange(volume: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(PlaybackData(volume = volume.toFloat(), appName = playbackData.value?.appName, mediaAction = MediaAction.Volume))
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
