package sefirah.domain.model

enum class NotificationType {
    Active,
    New,
    Removed,
    Invoke
}

enum class ConversationType {
    Active,
    ActiveUpdated,
    New,
    Removed,
}

enum class SessionType {
    PlaybackInfo,
    PlaybackUpdate,
    TimelineUpdate,
    RemovedSession
}

enum class PlaybackActionType {
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

enum class CommandType {
    Disconnect,
    ClearNotifications,
    RequestAppList
}

enum class AudioMessageType {
    New,
    Removed,
    Active
}

