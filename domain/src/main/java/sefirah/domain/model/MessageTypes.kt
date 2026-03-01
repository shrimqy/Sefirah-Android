package sefirah.domain.model

enum class NotificationInfoType {
    Active,
    New,
    Removed,
    Invoke
}

enum class ConversationInfoType {
    Active,
    ActiveUpdated,
    New,
    Removed,
}

enum class PlaybackInfoType {
    PlaybackInfo,
    PlaybackUpdate,
    TimelineUpdate,
    RemovedSession
}

enum class MediaActionType {
    Play,
    Pause,
    Next,
    Previous,
    Seek,
    Shuffle,
    Repeat,
    PlaybackRate,
    DefaultDevice,
    VolumeUpdate,
    ToggleMute
}

enum class AudioInfoType {
    New,
    Removed,
    Active
}

enum class CallState {
    Ringing,
    InProgress,
    MissedCall
}

