package sefirah.domain.repository

import kotlinx.coroutines.flow.StateFlow
import sefirah.domain.model.PlaybackData

interface PlaybackRepository {
     fun updatePlaybackData(data: PlaybackData)
     fun readPlaybackData(): StateFlow<PlaybackData?>
}