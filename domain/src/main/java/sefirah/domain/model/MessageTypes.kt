package sefirah.domain.model

enum class NotificationInfoType {
    Active,
    New,
    Removed,
    Invoke
}

enum class MessageListKind {
    Open,
    History,
    New,
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
    VolumeUpdate
}

enum class AudioActionType {
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

enum class CallLogType {
    Incoming,
    Outgoing,
    Missed,
    Voicemail,
    Rejected,
    Blocked,
    AnsweredExternally,
    Unknown,
}