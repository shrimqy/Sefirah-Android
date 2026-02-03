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
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.AudioDeviceInfo
import sefirah.domain.model.AudioInfoType
import sefirah.domain.model.MediaAction
import sefirah.domain.model.MediaActionType
import sefirah.domain.model.PlaybackInfo
import sefirah.domain.model.PlaybackInfoType
import sefirah.common.util.base64ToBitmap
import sefirah.projection.media.MediaActionReceiver.Companion.ACTION_NEXT
import sefirah.projection.media.MediaActionReceiver.Companion.ACTION_PLAY
import sefirah.projection.media.MediaActionReceiver.Companion.ACTION_PREVIOUS
import sefirah.projection.media.MediaActionReceiver.Companion.EXTRA_DEVICE_ID
import sefirah.projection.media.MediaActionReceiver.Companion.EXTRA_SOURCE
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemotePlaybackHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val notificationCenter: NotificationCenter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var mediaSession: MediaSessionCompat? = null

    private val _activeSessionsByDevice = MutableStateFlow<Map<String, List<PlaybackInfo>>>(emptyMap())
    val activeSessionsByDevice: StateFlow<Map<String, List<PlaybackInfo>>> = _activeSessionsByDevice.asStateFlow()
    
    private val _audioDevicesByDevice = MutableStateFlow<Map<String, List<AudioDeviceInfo>>>(emptyMap())
    val audioDevicesByDevice: StateFlow<Map<String, List<AudioDeviceInfo>>> = _audioDevicesByDevice.asStateFlow()
    
    private var lastPositionUpdateTimeMap = mutableMapOf<String, Long>()

    fun handleAudioDevice(remoteDeviceId: String, device: AudioDeviceInfo) {
        when (device.infoType) {
            AudioInfoType.New, AudioInfoType.Active -> addOrUpdateAudioDevice(remoteDeviceId, device)
            AudioInfoType.Removed -> removeAudioDevice(remoteDeviceId, device)
        }
    }

    private fun addOrUpdateAudioDevice(remoteDeviceId: String, device: AudioDeviceInfo) {
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
    
    private fun removeAudioDevice(remoteDeviceId: String, device: AudioDeviceInfo) {
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

    fun handlePlaybackSessionUpdates(deviceId: String, playbackSession: PlaybackInfo) {
        when (playbackSession.infoType) {
            PlaybackInfoType.PlaybackInfo -> addSession(deviceId, playbackSession)
            PlaybackInfoType.RemovedSession -> removeSession(deviceId, playbackSession)
            PlaybackInfoType.TimelineUpdate -> updateTimeline(deviceId, playbackSession)
            PlaybackInfoType.PlaybackUpdate -> updatePlaybackInfo(deviceId, playbackSession)
        }
    }

    private fun updateTimeline(deviceId: String, playbackSession: PlaybackInfo) {
        _activeSessionsByDevice.update { currentMap ->
            val currentSessions = currentMap.getOrDefault(deviceId, emptyList())
            val updatedSessions = currentSessions.map { session ->
                if (session.source == playbackSession.source) {
                    val updatedSession = session.copy(position = playbackSession.position)
                    lastPositionUpdateTimeMap[session.source] = System.currentTimeMillis()
                    showMediaSession(deviceId, updatedSession)
                    updatedSession
                } else {
                    session
                }
            }
            currentMap + (deviceId to updatedSessions)
        }
    }


    private fun updatePlaybackInfo(deviceId: String, playbackSession: PlaybackInfo) {
        _activeSessionsByDevice.update { currentMap ->
            val currentSessions = currentMap.getOrDefault(deviceId, emptyList())
            val updatedSessions = currentSessions.map { session ->
                if (session.source == playbackSession.source) {
                    val updatedSession = session.copy(
                        isPlaying = playbackSession.isPlaying,
                        isShuffleActive = playbackSession.isShuffleActive,
                        playbackRate = playbackSession.playbackRate)
                    showMediaSession(deviceId, updatedSession)
                    updatedSession
                } else {
                    session
                }
            }
            currentMap + (deviceId to updatedSessions)
        }
    }

    private fun addSession(deviceId: String, session: PlaybackInfo) {
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

    private fun removeSession(deviceId: String, session: PlaybackInfo) {
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


    private fun showMediaSession(deviceId: String, session: PlaybackInfo) {
        scope.launch(Dispatchers.Main) {
            val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, session.trackTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, session.artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        session.maxSeekTime?.toLong() ?: 0
                    )
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
                    handleMediaAction(deviceId, MediaAction(MediaActionType.Play, session.source))
                }

                override fun onPause() {
                    handleMediaAction(deviceId, MediaAction(MediaActionType.Pause, session.source))
                }

                override fun onSkipToNext() {
                    handleMediaAction(deviceId, MediaAction(MediaActionType.Next, session.source))
                }

                override fun onSkipToPrevious() {
                    handleMediaAction(deviceId, MediaAction(MediaActionType.Previous, session.source))
                }

                override fun onSeekTo(pos: Long) {
                    handleMediaAction(deviceId, MediaAction(MediaActionType.Seek, session.source, pos.toDouble()))
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

            // Get or create MediaSession
            val mediaSession = this@RemotePlaybackHandler.mediaSession ?: try {
                MediaSessionCompat(context, MEDIA_SESSION_TAG).also {
                    this@RemotePlaybackHandler.mediaSession = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create MediaSession", e)
                return@launch
            }

            mediaSession.setCallback(mediaSessionCallback, Handler(context.mainLooper))
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
                        val action = MediaAction(MediaActionType.VolumeUpdate, currentAudioDevice.deviceId, normalizedVolume.toDouble())
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
        }
    }

    fun handleMediaAction(deviceId: String, action: MediaAction) {
        Log.d(TAG, "Action received: ${action.actionType}${action.source} device: $deviceId")
        networkManager.sendMessage(deviceId, action)
    }

    fun clearDeviceData(deviceId: String) {
        _activeSessionsByDevice.update { it - deviceId }
        _audioDevicesByDevice.update { it - deviceId }

        // Remove position updates for sessions from this device
        _activeSessionsByDevice.value[deviceId]?.forEach { session ->
            lastPositionUpdateTimeMap.remove(session.source)
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

    private fun PlaybackInfo.getCurrentPosition(): Double {
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



