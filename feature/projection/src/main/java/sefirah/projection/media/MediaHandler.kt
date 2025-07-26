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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.PlaybackAction
import sefirah.domain.model.PlaybackActionType
import sefirah.domain.model.PlaybackSession
import sefirah.domain.model.SessionType
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.presentation.util.base64ToBitmap
import javax.inject.Inject

class MediaHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val notificationCenter: NotificationCenter,
    private val preferencesRepository: PreferencesRepository
) {
    private var mediaSession: MediaSessionCompat? = null
    
    // Replace mutable lists with StateFlows
    private val _activeSessions = MutableStateFlow<List<PlaybackSession>>(emptyList())
    val activeSessions: StateFlow<List<PlaybackSession>> = _activeSessions.asStateFlow()
    
    private val _audioDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val audioDevices: StateFlow<List<AudioDevice>> = _audioDevices.asStateFlow()
    
    private var lastPositionUpdateTimeMap = mutableMapOf<String, Long>()

    fun addAudioDevice(device: AudioDevice) {
        _audioDevices.update { currentDevices -> 
            val existingIndex = currentDevices.indexOfFirst { it.deviceId == device.deviceId }
            if (existingIndex >= 0) {
                currentDevices.toMutableList().apply {
                    this[existingIndex] = device
                }
            } else {
                currentDevices + device 
            }
        }
    }

    fun updateAudioDeviceVolume(device: AudioDevice, volume: Float) {
        _audioDevices.update { currentDevices ->
            currentDevices.map {
                if (it.deviceId == device.deviceId) {
                    it.copy(volume = volume)
                } else {
                    it
                }
            }
        }
        handleMediaAction(
            PlaybackAction(
                playbackActionType = PlaybackActionType.VolumeUpdate,
                source = device.deviceId,
                value = volume.toDouble()
            )
        )
    }

    fun defaultAudioDevice(audioDevice: AudioDevice) {
        _audioDevices.update { devices ->
            devices.map { device -> 
                if (device.deviceId == audioDevice.deviceId) {
                    device.copy(isSelected = true)
                } else {
                    device.copy(isSelected = false)
                }
            }
        }
    }

    fun removeAudioDevice(device: AudioDevice) {
        _audioDevices.update { currentDevices -> 
            currentDevices.filter { it.deviceId != device.deviceId }
        }
    }

    fun toggleMute(device: AudioDevice) {
        _audioDevices.update { currentDevices ->
            currentDevices.map {
                if (it.deviceId == device.deviceId) {
                    device.copy(isMuted = !device.isMuted)
                } else {
                    device
                }
            }
        }
    }

    fun handlePlaybackSessionUpdates(playbackSession: PlaybackSession) {
        when (playbackSession.sessionType) {
            SessionType.Session -> addSession(playbackSession)
            SessionType.RemovedSession -> removeSession(playbackSession)
            SessionType.TimelineUpdate -> updateTimeline(playbackSession)
            SessionType.PlaybackInfoUpdate -> updatePlaybackInfo(playbackSession)
        }
    }

    private fun updateTimeline(playbackSession: PlaybackSession) {
        _activeSessions.update { currentSessions ->
            currentSessions.map { session ->
                if (session.source == playbackSession.source) {
                    val updatedSession = session.copy(position = playbackSession.position)
                    lastPositionUpdateTimeMap[session.source!!] = System.currentTimeMillis()
                    updateMediaSession(updatedSession)
                    updatedSession
                } else {
                    session
                }
            }
        }
    }


    private fun updatePlaybackInfo(playbackSession: PlaybackSession) {
        _activeSessions.update { currentSessions ->
            currentSessions.map { session ->
                if (session.source == playbackSession.source) {
                    val updatedSession = session.copy(
                        isPlaying = playbackSession.isPlaying,
                        isShuffle = playbackSession.isShuffle,
                        playbackRate = playbackSession.playbackRate)
                    updateMediaSession(updatedSession)
                    updatedSession
                } else {
                    session
                }
            }
        }
    }
    
    private fun addSession(session: PlaybackSession) {
        _activeSessions.update { currentSessions ->
            // First, get the modified list with the new/updated session
            val sessionsList = if (currentSessions.any { it.source == session.source }) {
                currentSessions.map { 
                    if (it.source == session.source) session else it 
                }
            } else {
                currentSessions + session
            }
            
            // If the new session is marked as current, make non-playing sessions not current
            if (session.isCurrentSession) {
                lastPositionUpdateTimeMap[session.source!!] = System.currentTimeMillis()
                updateMediaSession(session)

                sessionsList.map { existingSession ->
                    if (existingSession.source != session.source && !existingSession.isPlaying) {
                        // Make non-playing sessions not current
                        existingSession.copy(isCurrentSession = false)
                    } else {
                        existingSession
                    }
                }
            } else {
                sessionsList
            }
        }
    }

    private fun removeSession(session: PlaybackSession) {
        _activeSessions.update { currentSessions ->
            currentSessions.filter { it.source != session.source }
        }
        if (_activeSessions.value.isEmpty())
        {
            release()
        }

    }

    // Main Activity Intent
    private val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    private val mainPendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)


    private fun updateMediaSession(playbackSession: PlaybackSession) {
        CoroutineScope(Dispatchers.Main).launch {
            val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playbackSession.trackTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playbackSession.artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        playbackSession.maxSeekTime.toLong()
                    )
                    .putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        playbackSession.thumbnail?.let { base64ToBitmap(it) })

            val playbackState = PlaybackStateCompat.Builder()
                .setState(
                    if (playbackSession.isPlaying)
                        PlaybackStateCompat.STATE_PLAYING
                    else
                        PlaybackStateCompat.STATE_PAUSED,
                    playbackSession.getCurrentPosition().toLong(),
                    if (playbackSession.isPlaying) 1.0f else 0.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )

            val mediaSessionCallback : MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handleMediaAction(
                        PlaybackAction(
                            playbackActionType = PlaybackActionType.Play,
                            source = playbackSession.source,
                        )
                    )
                }

                override fun onPause() {
                    handleMediaAction(
                        PlaybackAction(
                            playbackActionType = PlaybackActionType.Pause,
                            source = playbackSession.source,
                        )
                    )
                }

                override fun onSkipToNext() {
                    handleMediaAction(
                        PlaybackAction(
                            playbackActionType = PlaybackActionType.Next,
                            source = playbackSession.source,
                        )
                    )
                }

                override fun onSkipToPrevious() {
                    handleMediaAction(
                        PlaybackAction(
                            playbackActionType = PlaybackActionType.Previous,
                            source = playbackSession.source,
                        )
                    )
                }

                override fun onSeekTo(pos: Long) {
                    handleMediaAction(
                        PlaybackAction(
                            playbackActionType = PlaybackActionType.Seek,
                            source = playbackSession.source,
                            value = pos.toDouble()
                        )
                    )
                }
            }

            // Add media control actions with PendingIntents
            val playPauseIntent = Intent(context, MediaActionReceiver::class.java).apply {
                action = if (playbackSession.isPlaying) "ACTION_PAUSE" else "ACTION_PLAY"
                putExtra(MediaActionReceiver.EXTRA_SOURCE, playbackSession.source)
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = Intent(context, MediaActionReceiver::class.java).apply {
                action = MediaActionReceiver.ACTION_PREVIOUS
                putExtra(MediaActionReceiver.EXTRA_SOURCE, playbackSession.source)
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(context, MediaActionReceiver::class.java).apply {
                action = MediaActionReceiver.ACTION_NEXT
                putExtra(MediaActionReceiver.EXTRA_SOURCE, playbackSession.source)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val mediaStyle = NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)

                val mediaSession = mediaSession ?: MediaSessionCompat(context, MEDIA_SESSION_TAG).apply {
                    setCallback(mediaSessionCallback, Handler(context.mainLooper))
                }
                mediaSession.setMetadata(metadata.build())
                mediaSession.setPlaybackState(playbackState.build())
                mediaStyle.setMediaSession(mediaSession.sessionToken)

                val currentAudioDevice = audioDevices.value.firstOrNull() { it.isSelected }
                mediaSession.setPlaybackToRemote(object : VolumeProviderCompat(
                    VOLUME_CONTROL_ABSOLUTE,
                    getMaxVolume(),
                    getCurrentVolume(currentAudioDevice?.volume)
                ) {
                    override fun onSetVolumeTo(volume: Int) {
                        currentVolume = volume
                        val normalizedVolume = volume.toFloat()  / maxVolume
                        CoroutineScope(Dispatchers.IO).launch {
                            if (currentAudioDevice != null) {
                                updateAudioDeviceVolume(currentAudioDevice, normalizedVolume)
                            }
                        }
                    }
                })

                notificationCenter.showNotification(
                    channelId = AppNotifications.MEDIA_PLAYBACK_CHANNEL,
                    notificationId = AppNotifications.MEDIA_PLAYBACK_ID,
                ) {
                    setStyle(mediaStyle)
                    mediaSession.isActive = true
                    setContentTitle(playbackSession.trackTitle)
                    setContentIntent(mainPendingIntent)
                    setContentText(playbackSession.artist)
                    setLargeIcon(playbackSession.thumbnail?.let { base64ToBitmap(it) })

                    addAction(
                        R.drawable.ic_previous,
                        "Previous",
                        prevPendingIntent
                    )
                    addAction(
                        if (playbackSession.isPlaying)
                            R.drawable.ic_pause
                        else
                            R.drawable.ic_play,
                        if (playbackSession.isPlaying) "Pause" else "Play",
                        playPausePendingIntent
                    )
                    addAction(
                        R.drawable.ic_next,
                        "Next",
                        nextPendingIntent
                    )
                    setSilent(true)
                    setOngoing(true)
                }
                if (this@MediaHandler.mediaSession == null) {
                    this@MediaHandler.mediaSession = mediaSession
                }
        }
    }

    fun handleMediaAction(action: PlaybackAction) {
        Log.d(TAG, "Action received: ${action.playbackActionType}" + action.source)
        CoroutineScope(Dispatchers.IO).launch {
            networkManager.sendMessage(action)
        }
    }

    fun release(isDisconnected: Boolean = false) {
        mediaSession?.let {
            it.isActive = false
            it.release()
            mediaSession = null
        }
        _activeSessions.value = emptyList()
        notificationCenter.cancelNotification(AppNotifications.MEDIA_PLAYBACK_ID)
        if (isDisconnected)
            _audioDevices.value = emptyList()
    }

    private fun getMaxVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    private fun getCurrentVolume(remoteVolume: Float?): Int {
        val maxVolume = getMaxVolume()
        return (remoteVolume?.times(maxVolume) ?: maxVolume).toInt()
    }

    private fun PlaybackSession.getCurrentPosition(): Double {
        val lastUpdateTime = lastPositionUpdateTimeMap[source] ?: System.currentTimeMillis()
        return if (isPlaying) {
            position + (System.currentTimeMillis() - lastUpdateTime) * 1000L
        } else {
            position
        }
    }

    companion object {
        private const val TAG = "MediaHandler"
        private const val MEDIA_SESSION_TAG = "DesktopMediaSession"
    }
}



