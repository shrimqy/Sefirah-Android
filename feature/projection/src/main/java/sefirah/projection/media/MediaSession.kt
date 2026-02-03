package sefirah.projection.media

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import sefirah.common.util.bitmapToBase64
import sefirah.domain.model.PlaybackInfo
import sefirah.domain.model.PlaybackInfoType

internal class MediaSession(
    val controller: MediaController,
    val appName: String
) {
    val packageName: String get() = controller.packageName

    fun isPlaying(): Boolean {
        val state = controller.playbackState ?: return false
        return state.state == PlaybackState.STATE_PLAYING
    }

    fun canPlay(): Boolean {
        val state = controller.playbackState ?: return false
        if (state.state == PlaybackState.STATE_PLAYING) return true
        val actions = state.actions
        return (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
    }

    fun canPause(): Boolean {
        val state = controller.playbackState ?: return false
        if (state.state == PlaybackState.STATE_PAUSED) return true
        val actions = state.actions
        return (actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
    }

    fun canGoNext(): Boolean {
        val state = controller.playbackState ?: return false
        return (state.actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
    }

    fun canGoPrevious(): Boolean {
        val state = controller.playbackState ?: return false
        return (state.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
    }

    fun canSeek(): Boolean {
        val state = controller.playbackState ?: return false
        return (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L
    }

    fun getTitle(): String =
        firstNonEmpty(
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ).orEmpty()

    fun getArtist(): String =
        firstNonEmpty(
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR),
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_WRITER)
        ).orEmpty()

    fun getAlbum(): String = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

    fun getPosition(): Long = controller.playbackState?.position ?: 0L

    fun getLength(): Long = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    fun getVolume(): Int {
        val info = controller.playbackInfo
        val maxVolume = info.maxVolume
        return if (maxVolume != 0) 100 * info.currentVolume / maxVolume else 0
    }

    fun play(): Unit = controller.transportControls.play()
    fun pause(): Unit = controller.transportControls.pause()
    fun playPause(): Unit = if (isPlaying()) pause() else play()
    fun next(): Unit = controller.transportControls.skipToNext()
    fun previous(): Unit = controller.transportControls.skipToPrevious()
    fun setPosition(position: Long): Unit = controller.transportControls.seekTo(position)

    fun setVolume(volumePercent: Int) {
        val info = controller.playbackInfo
        val maxVolume = info.maxVolume
        val target = (maxVolume * volumePercent / 100.0 + 0.5).toInt().coerceIn(0, maxVolume)
        controller.setVolumeTo(target, 0)
    }

    private fun firstNonEmpty(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    fun toPlaybackSession(playbackInfoType: PlaybackInfoType = PlaybackInfoType.PlaybackInfo): PlaybackInfo {
        val metadata = controller.metadata
        val thumbnail = try {
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { bitmapToBase64(it) }
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { bitmapToBase64(it) }
        } catch (e: Exception) {
            null
        }
        return PlaybackInfo(
            infoType = playbackInfoType,
            source = packageName,
            trackTitle = getTitle().takeIf { it.isNotEmpty() },
            artist = getArtist().takeIf { it.isNotEmpty() },
            isPlaying = isPlaying(),
            position = getPosition().toDouble(),
            maxSeekTime = getLength().toDouble(),
            thumbnail = thumbnail,
            appName = appName,
            volume = getVolume(),
            canPlay = canPlay(),
            canPause = canPause(),
            canGoNext = canGoNext(),
            canGoPrevious = canGoPrevious(),
            canSeek = canSeek()
        )
    }
}