package sefirah.projection.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.VolumeProviderCompat
import androidx.media.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.AudioMessageType
import sefirah.domain.model.PlaybackAction
import sefirah.domain.model.PlaybackActionType
import sefirah.domain.model.PlaybackSession
import sefirah.domain.model.SessionType
import sefirah.domain.repository.NetworkManager
import sefirah.presentation.util.base64ToBitmap
import sefirah.projection.media.MediaActionReceiver.Companion.ACTION_NEXT
import sefirah.projection.media.MediaActionReceiver.Companion.ACTION_PLAY
import sefirah.projection.media.MediaActionReceiver.Companion.ACTION_PREVIOUS
import sefirah.projection.media.MediaActionReceiver.Companion.EXTRA_DEVICE_ID
import sefirah.projection.media.MediaActionReceiver.Companion.EXTRA_SOURCE
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val notificationCenter: NotificationCenter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var mediaSession: MediaSessionCompat? = null

    private val _activeSessionsByDevice = MutableStateFlow<Map<String, List<PlaybackSession>>>(emptyMap())
    val activeSessionsByDevice: StateFlow<Map<String, List<PlaybackSession>>> = _activeSessionsByDevice.asStateFlow()
    
    private val _audioDevicesByDevice = MutableStateFlow<Map<String, List<AudioDevice>>>(emptyMap())
    val audioDevicesByDevice: StateFlow<Map<String, List<AudioDevice>>> = _audioDevicesByDevice.asStateFlow()
    
    private var lastPositionUpdateTimeMap = mutableMapOf<String, Long>()

    fun handleAudioDevice(remoteDeviceId: String, device: AudioDevice) {
        when (device.audioDeviceType) {
            AudioMessageType.New, AudioMessageType.Active -> addOrUpdateAudioDevice(remoteDeviceId, device)
            AudioMessageType.Removed -> removeAudioDevice(remoteDeviceId, device)
        }
    }

    private fun addOrUpdateAudioDevice(remoteDeviceId: String, device: AudioDevice) {
        _audioDevicesByDevice.update { currentMap ->
            val currentDevices = currentMap.getOrDefault(remoteDeviceId, emptyList())
            val updatedDevices = if (currentDevices.any { it.deviceId == device.deviceId }) {
                currentDevices.map { if (it.deviceId == device.deviceId) device else it }
            } else {
                currentDevices + device
            }
            currentMap + (remoteDeviceId to updatedDevices)
        }
    }
    
    private fun removeAudioDevice(remoteDeviceId: String, device: AudioDevice) {
        _audioDevicesByDevice.update { currentMap ->
            val currentDevices = currentMap.getOrDefault(remoteDeviceId, emptyList())
            val updatedDevices = currentDevices.filter { it.deviceId != device.deviceId }
            if (updatedDevices.isEmpty()) {
                currentMap - remoteDeviceId
            } else {
                currentMap + (remoteDeviceId to updatedDevices)
            }
        }
    }

    fun handlePlaybackSessionUpdates(deviceId: String, playbackSession: PlaybackSession) {
        when (playbackSession.sessionType) {
            SessionType.PlaybackInfo -> addSession(deviceId, playbackSession)
            SessionType.RemovedSession -> removeSession(deviceId, playbackSession)
            SessionType.TimelineUpdate -> updateTimeline(deviceId, playbackSession)
            SessionType.PlaybackUpdate -> updatePlaybackInfo(deviceId, playbackSession)
        }
    }

    private fun updateTimeline(deviceId: String, playbackSession: PlaybackSession) {
        _activeSessionsByDevice.update { currentMap ->
            val currentSessions = currentMap.getOrDefault(deviceId, emptyList())
            val updatedSessions = currentSessions.map { session ->
                if (session.source == playbackSession.source) {
                    val updatedSession = session.copy(position = playbackSession.position)
                    lastPositionUpdateTimeMap[session.source!!] = System.currentTimeMillis()
                    showMediaSession(deviceId, updatedSession)
                    updatedSession
                } else {
                    session
                }
            }
            currentMap + (deviceId to updatedSessions)
        }
    }


    private fun updatePlaybackInfo(deviceId: String, playbackSession: PlaybackSession) {
        _activeSessionsByDevice.update { currentMap ->
            val currentSessions = currentMap.getOrDefault(deviceId, emptyList())
            val updatedSessions = currentSessions.map { session ->
                if (session.source == playbackSession.source) {
                    val updatedSession = session.copy(
                        isPlaying = playbackSession.isPlaying,
                        isShuffle = playbackSession.isShuffle,
                        playbackRate = playbackSession.playbackRate)
                    showMediaSession(deviceId, updatedSession)
                    updatedSession
                } else {
                    showMediaSession(deviceId, session)
                    session
                }
            }
            currentMap + (deviceId to updatedSessions)
        }
    }

    private fun addSession(deviceId: String, session: PlaybackSession) {
        _activeSessionsByDevice.update { currentMap ->
            val currentSessions = currentMap.getOrDefault(deviceId, emptyList())
            val updatedSessions = if (currentSessions.any { it.source == session.source }) {
                currentSessions.map { if (it.source == session.source) session else it }
            } else {
                currentSessions + session
            }
            currentMap + (deviceId to updatedSessions)
        }
        showMediaSession(deviceId, session)
    }

    private fun removeSession(deviceId: String, session: PlaybackSession) {
        _activeSessionsByDevice.update { currentMap ->
            val currentSessions = currentMap.getOrDefault(deviceId, emptyList())
            val updatedSessions = currentSessions.filter { it.source != session.source }
            if (updatedSessions.isEmpty()) {
                currentMap - deviceId
            } else {
                showMediaSession(deviceId, updatedSessions.first())
                currentMap + (deviceId to updatedSessions)
            }
        }
        // Release if no sessions exist for any device
        if (_activeSessionsByDevice.value.isEmpty()) {
            release()
        }
    }

    private val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    private val mainPendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)


    private fun showMediaSession(deviceId: String, session: PlaybackSession) {
        scope.launch(Dispatchers.Main) {
            val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, session.trackTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, session.artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, session.maxSeekTime.toLong())
                    .putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        session.thumbnail?.let { base64ToBitmap(it) }
                    )

            val playbackState = PlaybackStateCompat.Builder()
                .setState(
                    if (session.isPlaying)
                        PlaybackStateCompat.STATE_PLAYING
                    else
                        PlaybackStateCompat.STATE_PAUSED,
                    session.getCurrentPosition().toLong(),
                    if (session.isPlaying) 1.0f else 0.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )

            val mediaSessionCallback : MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    val action = PlaybackAction(PlaybackActionType.Play, session.source)
                    handleMediaAction(deviceId, action)
                }

                override fun onPause() {
                    val action = PlaybackAction(PlaybackActionType.Pause, session.source)
                    handleMediaAction(deviceId, action)
                }

                override fun onSkipToNext() {
                    val action = PlaybackAction(PlaybackActionType.Next, session.source)
                    handleMediaAction(deviceId, action)
                }

                override fun onSkipToPrevious() {
                    val action = PlaybackAction(PlaybackActionType.Previous, session.source)
                    handleMediaAction(deviceId, action)
                }

                override fun onSeekTo(pos: Long) {
                    val action = PlaybackAction(PlaybackActionType.Seek, session.source, pos.toDouble())
                    handleMediaAction(deviceId, action)
                }
            }

            // Add media control actions with PendingIntents
            val playPauseIntent = Intent(context, MediaActionReceiver::class.java).apply {
                action = if (session.isPlaying) MediaActionReceiver.ACTION_PAUSE else ACTION_PLAY
                putExtra(EXTRA_SOURCE, session.source)
                putExtra(EXTRA_DEVICE_ID, deviceId)
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = Intent(context, MediaActionReceiver::class.java).apply {
                action = ACTION_PREVIOUS
                putExtra(EXTRA_SOURCE, session.source)
                putExtra(EXTRA_DEVICE_ID, deviceId)
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(context, MediaActionReceiver::class.java).apply {
                action = ACTION_NEXT
                putExtra(EXTRA_SOURCE, session.source)
                putExtra(EXTRA_DEVICE_ID, deviceId)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val mediaStyle = NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2)

            val mediaSession = mediaSession ?: MediaSessionCompat(context, MEDIA_SESSION_TAG).apply {
                setCallback(mediaSessionCallback, Handler(context.mainLooper))
            }
            mediaSession.setMetadata(metadata.build())
            mediaSession.setPlaybackState(playbackState.build())
            mediaStyle.setMediaSession(mediaSession.sessionToken)

            val audioDevicesForDevice = _audioDevicesByDevice.value[deviceId] ?: emptyList()
            val currentAudioDevice = audioDevicesForDevice.firstOrNull { it.isSelected } ?: audioDevicesForDevice.firstOrNull()
            mediaSession.setPlaybackToRemote(object : VolumeProviderCompat(
                VOLUME_CONTROL_ABSOLUTE,
                getMaxVolume(),
                getCurrentVolume(currentAudioDevice?.volume)
            ) {
                override fun onSetVolumeTo(volume: Int) {
                    currentVolume = volume
                    val normalizedVolume = volume.toFloat() / maxVolume
                    if (currentAudioDevice != null) {
                        val action = PlaybackAction(PlaybackActionType.VolumeUpdate, currentAudioDevice.deviceId, normalizedVolume.toDouble())
                        handleMediaAction(deviceId, action)
                    }
                }
            })

            notificationCenter.showNotification(
                channelId = AppNotifications.MEDIA_PLAYBACK_CHANNEL,
                notificationId = AppNotifications.MEDIA_PLAYBACK_ID,
            ) {
                setStyle(mediaStyle)
                mediaSession.isActive = true
                setContentTitle(session.trackTitle)
                setContentIntent(mainPendingIntent)
                setContentText(session.artist)
                setLargeIcon(session.thumbnail?.let { base64ToBitmap(it) })

                addAction(R.drawable.ic_skip_previous, "Previous", prevPendingIntent)
                addAction(
                    if (session.isPlaying)
                        R.drawable.ic_pause
                    else
                        R.drawable.ic_play_arrow,
                    if (session.isPlaying) "Pause" else "Play",
                    playPausePendingIntent
                )
                addAction(R.drawable.ic_skip_next, "Next", nextPendingIntent)
                setSilent(true)
                setOngoing(true)
            }
            if (this@MediaHandler.mediaSession == null) {
                this@MediaHandler.mediaSession = mediaSession
            }
        }
    }

    fun handleMediaAction(deviceId: String, action: PlaybackAction) {
        Log.d(TAG, "Action received: ${action.playbackActionType}" + action.source)
        networkManager.sendMessage(deviceId, action)
    }

    fun clearDeviceData(deviceId: String) {
        _activeSessionsByDevice.update { it - deviceId }
        _audioDevicesByDevice.update { it - deviceId }

        // Remove position updates for sessions from this device
        _activeSessionsByDevice.value[deviceId]?.forEach { session ->
            session.source?.let { lastPositionUpdateTimeMap.remove(it) }
        }
        // Release media session if no sessions exist for any device
        if (_activeSessionsByDevice.value.isEmpty()) {
            release()
        }
    }

    fun release() {
        mediaSession?.let {
            it.isActive = false
            it.release()
            mediaSession = null
        }
        _activeSessionsByDevice.value = emptyMap()
        notificationCenter.cancelNotification(AppNotifications.MEDIA_PLAYBACK_ID)
    }

    private fun getMaxVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    private fun getCurrentVolume(volume: Float?): Int {
        val maxVolume = getMaxVolume()
        return (volume?.times(maxVolume) ?: maxVolume).toInt()
    }

    private fun PlaybackSession.getCurrentPosition(): Double {
        val lastUpdateTime = lastPositionUpdateTimeMap[source] ?: System.currentTimeMillis()
        return if (isPlaying) {
            val elapsedTime = System.currentTimeMillis() - lastUpdateTime
            val rate = this.playbackRate ?: 1.0
            val calculatedPosition = position + (elapsedTime * rate)
            minOf(calculatedPosition, maxSeekTime)
        } else {
            position
        }
    }

    companion object {
        private const val TAG = "MediaHandler"
        private const val MEDIA_SESSION_TAG = "DesktopMediaSession"
    }
}



