package sefirah.media

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.domain.model.MediaAction
import sefirah.domain.model.PlaybackData
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.presentation.util.base64ToBitmap
import javax.inject.Inject

class MediaService @Inject constructor(
    val context: Context,
    private val networkManager: NetworkManager,
    private val notificationCenter: NotificationCenter,
    private val preferencesRepository: PreferencesRepository
) : MediaHandler {
    private val channelName by lazy { context.getString(R.string.notification_media_playback) }
    private val channelId = "Desktop Media Playback"
    private val notificationId = channelId.hashCode()

    private val mediaSession by lazy {
        MediaSessionCompat(context, MEDIA_SESSION_TAG).apply {
            isActive = true
        }
    }

    override fun updateMediaSession(playbackData: PlaybackData) {
        CoroutineScope(Dispatchers.Main).launch {
            mediaSession.apply {
                setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playbackData.trackTitle)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playbackData.artist)
                        .putBitmap(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            playbackData.thumbnail?.let { base64ToBitmap(it) })
                        .build()
                )

                // TODO: Add seek action
                setPlaybackState(
                    playbackData.position?.let {
                        PlaybackStateCompat.Builder()
                            .setState(
                                if (playbackData.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                            )
                            .setActions(
                                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                            )
                            .build()
                    }
                )

                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        handleMediaAction(playbackData, MediaAction.Resume)
                    }

                    override fun onPause() {
                        handleMediaAction(playbackData, MediaAction.Pause)
                    }

                    override fun onSkipToNext() {
                        handleMediaAction(playbackData, MediaAction.NextQueue)
                    }

                    override fun onSkipToPrevious() {
                        handleMediaAction(playbackData, MediaAction.PrevQueue)
                    }
                })
            }

            notificationCenter.showNotification(
                channelId = AppNotifications.MEDIA_PLAYBACK_CHANNEL,
                notificationId = AppNotifications.MEDIA_PLAYBACK_ID,
            ) {
                setContentTitle(playbackData.trackTitle)
                setContentText(playbackData.artist)
                setLargeIcon(playbackData.thumbnail?.let { base64ToBitmap(it) })
                setStyle(
                    NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                setSilent(true)
                setOngoing(true)
            }
        }

        Log.d(TAG, "Notification updated for playback: ${playbackData.trackTitle}")
    }

    fun handleMediaAction(
        playbackData: PlaybackData,
        action: MediaAction,
    ) {
        playbackData.mediaAction = action
        CoroutineScope(Dispatchers.IO).launch {
            networkManager.sendMessage(PlaybackData(appName = playbackData.appName, mediaAction = action))
        }
        Log.d(TAG, "Action received: $action" + playbackData.trackTitle)
    }

    override fun release() {
        mediaSession.release()
    }

    companion object {
        private const val TAG = "MediaHandler"
        private const val MEDIA_SESSION_TAG = "DesktopMediaSession"
        private const val MEDIA_NOTIFICATION_GROUP = "file_transfer_group"
        private const val SEEK_FORWARD_INCREMENT = 10_000L
        private const val SEEK_BACKWARD_INCREMENT = 10_000L
    }
}



