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
import sefirah.common.extensions.NotificationCenter
import sefirah.domain.model.MediaAction
import sefirah.domain.model.PlaybackData
import sefirah.domain.repository.NetworkManager
import sefirah.presentation.util.base64ToBitmap
import javax.inject.Inject

class MediaService @Inject constructor(
    val context: Context,
    private val networkManager: NetworkManager,
    private val notificationCenter: NotificationCenter
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

            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        if (playbackData.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                    )
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                    .build()
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handleMediaAction(playbackData, MediaAction.RESUME)
                }

                override fun onPause() {
                    handleMediaAction(playbackData, MediaAction.PAUSE)
                }

                override fun onSkipToNext() {
                    handleMediaAction(playbackData, MediaAction.NEXT_QUEUE)
                }

                override fun onSkipToPrevious() {
                    handleMediaAction(playbackData, MediaAction.PREV_QUEUE)
                }
            })
        }

        notificationCenter.showNotification(
            channelId = channelId,
            channelName = channelName,
            notificationId = notificationId
        ) {
            setContentTitle(playbackData.trackTitle)
            setContentText(playbackData.artist)
            setLargeIcon(playbackData.thumbnail?.let { base64ToBitmap(it) })
            setStyle(
                NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            setOngoing(true)
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
    }
}



