package sefirah.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sefirah.domain.model.PlaybackData
import sefirah.domain.repository.PlaybackRepository
import javax.inject.Inject

class PlaybackRepositoryImpl @Inject constructor() : PlaybackRepository {
    private val _playbackData = MutableStateFlow<PlaybackData?>(null)
    private val playbackData: StateFlow<PlaybackData?> = _playbackData

    override fun updatePlaybackData(data: PlaybackData) {
        _playbackData.value = data
    }

    override fun readPlaybackData(): StateFlow<PlaybackData?> {
        return playbackData;
    }
}