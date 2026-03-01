package sefirah.domain.model

data class DevicePreferences(
    val clipboardSync: Boolean = false,
    val messageSync: Boolean = false,
    val notificationSync: Boolean = false,
    val callStateSync: Boolean = false,
    val imageClipboard: Boolean = false,
    val mediaSession: Boolean = false,
    val mediaPlayerControl: Boolean = false,
    val remoteStorage: Boolean = false,
)