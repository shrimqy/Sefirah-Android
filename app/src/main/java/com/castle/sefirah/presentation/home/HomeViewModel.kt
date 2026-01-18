package com.castle.sefirah.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import sefirah.domain.model.ActionMessage
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.PlaybackAction
import sefirah.domain.model.PlaybackActionType
import sefirah.domain.model.PlaybackSession
import sefirah.domain.model.SocketMessage
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.projection.media.MediaHandler
import sefirah.network.extensions.ActionHandler
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaHandler: MediaHandler,
    private val deviceManager: DeviceManager,
    private val networkManager: NetworkManager,
    actionHandler: ActionHandler
) : ViewModel() {

    val activeSessions: StateFlow<List<PlaybackSession>> = deviceManager.selectedDeviceId
        .flatMapLatest { deviceId ->
            mediaHandler.activeSessionsByDevice.map { it[deviceId] ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioDevices: StateFlow<List<AudioDevice>> = deviceManager.selectedDeviceId
        .flatMapLatest { deviceId ->
            mediaHandler.audioDevicesByDevice.map { it[deviceId] ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actions: StateFlow<List<ActionMessage>> = deviceManager.selectedDeviceId
        .flatMapLatest { deviceId ->
            actionHandler.actionsByDevice.map { it[deviceId] ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onPlayPause(session: PlaybackSession) = sendMessageToSelectedDevice(
        PlaybackAction(if (session.isPlaying) PlaybackActionType.Pause else PlaybackActionType.Play, session.source)
    )

    fun onNext(session: PlaybackSession) = sendMessageToSelectedDevice(
        PlaybackAction(PlaybackActionType.Next, session.source)
    )

    fun onPrevious(session: PlaybackSession) = sendMessageToSelectedDevice(
        PlaybackAction(PlaybackActionType.Previous, session.source)
    )

    fun onSeek(session: PlaybackSession, position: Double) = sendMessageToSelectedDevice(
        PlaybackAction(PlaybackActionType.Seek, session.source, position)
    )

    fun onVolumeChange(device: AudioDevice, volume: Float) {
        sendMessageToSelectedDevice(PlaybackAction(PlaybackActionType.VolumeUpdate, device.deviceId, volume.toDouble()))
    }

    fun toggleMute(device: AudioDevice) {
        sendMessageToSelectedDevice(PlaybackAction(PlaybackActionType.ToggleMute, device.deviceId))
    }

    fun setDefaultDevice(device: AudioDevice) {
        sendMessageToSelectedDevice(PlaybackAction(PlaybackActionType.DefaultDevice, device.deviceId))
    }

    fun sendMessageToSelectedDevice(message: SocketMessage) {
        deviceManager.selectedDeviceId.value?.let { deviceId ->
            networkManager.sendMessage(deviceId, message)
        }
    }
}
