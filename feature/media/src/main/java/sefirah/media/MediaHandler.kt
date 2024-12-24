package sefirah.media

import sefirah.domain.model.PlaybackData

interface MediaHandler {
    fun updateMediaSession(playbackData: PlaybackData)
    fun release()
}