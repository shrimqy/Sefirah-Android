package com.castle.sefirah.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sefirah.domain.model.ActionMessage
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.PlaybackAction
import sefirah.domain.model.PlaybackActionType
import sefirah.domain.model.PlaybackSession
import sefirah.domain.model.SocketMessage
import sefirah.domain.repository.NetworkManager
import sefirah.projection.media.MediaHandler
import javax.inject.Inject
import sefirah.network.extensions.ActionHandler

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val networkManager: NetworkManager,
    private val mediaHandler: MediaHandler,
    private val actionHandler: ActionHandler,
    application: Application
) : AndroidViewModel(application) {

    val activeSessions: StateFlow<List<PlaybackSession>> = mediaHandler.activeSessions
    val audioDevices: StateFlow<List<AudioDevice>> = mediaHandler.audioDevices
    val actions: StateFlow<List<ActionMessage>> = actionHandler.actions

    fun onPlayPause(session: PlaybackSession) {
        viewModelScope.launch(Dispatchers.IO) {
            val actionType = if (session.isPlaying) 
                PlaybackActionType.Pause 
            else 
                PlaybackActionType.Play
                
            sendMessage(
                PlaybackAction(
                    playbackActionType = actionType,
                    source = session.source
                )
            )
        }
    }

    fun onNext(session: PlaybackSession) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(
                PlaybackAction(
                    playbackActionType = PlaybackActionType.Next,
                    source = session.source
                )
            )
        }
    }

    fun onPrevious(session: PlaybackSession) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(
                PlaybackAction(
                    playbackActionType = PlaybackActionType.Previous,
                    source = session.source
                )
            )
        }
    }

    fun onSeek(session: PlaybackSession, position: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(
                PlaybackAction(
                    playbackActionType = PlaybackActionType.Seek,
                    source = session.source,
                    value = position
                )
            )
        }
    }
    
    fun onVolumeChange(device: AudioDevice, volume: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaHandler.updateAudioDeviceVolume(device, volume)
        }
    }

    fun toggleMute(device: AudioDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaHandler.toggleMute(device)
            sendMessage(
                PlaybackAction(
                    playbackActionType = PlaybackActionType.ToggleMute,
                    source = device.deviceId
                )
            )
        }
    }

    fun setDefaultDevice(device: AudioDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaHandler.defaultAudioDevice(device)
            sendMessage(
                PlaybackAction(
                    playbackActionType = PlaybackActionType.DefaultDevice,
                    source = device.deviceId
                )
            )
        }
    }

    fun sendAction(action: ActionMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMessage(action)
        }
    }

    private suspend fun sendMessage(message: SocketMessage) {
        networkManager.sendMessage(message)
    }
}
