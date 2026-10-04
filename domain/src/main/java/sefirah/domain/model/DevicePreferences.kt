package sefirah.domain.model

data class DevicePreferences(
    val clipboardSync: Boolean = false,
    val messageSync: Boolean = false,
    val longTextAsMms: Boolean = false,
    val groupMessageAsMms: Boolean = true,
    val convertToMmsAfter: Int = 3,
    val notificationSync: Boolean = false,
    val callStateSync: Boolean = false,
    val callLogSync: Boolean = false,
    val imageClipboard: Boolean = false,
    val mediaSession: Boolean = false,
    val mediaSessionNotification: Boolean = false,
    val remoteVolumeControl: Boolean = false,
    val mediaPlayerControl: Boolean = false,
    val remoteStorage: Boolean = false,
    val playSound: Boolean = false,
)