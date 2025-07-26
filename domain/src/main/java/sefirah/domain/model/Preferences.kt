package sefirah.domain.model

enum class DiscoveryMode {
    AUTO,
    ALWAYS_ON,
    DISABLED
}

data class PreferencesSettings( 
    val language: String,
    val discoveryMode: DiscoveryMode,
    val storageLocation: String,
    val readSensitiveNotifications: Boolean,
    val notificationSync: Boolean,
    val mediaSession: Boolean,
    val clipboardSync: Boolean,
    val messageSync: Boolean,
    val imageClipboard: Boolean,
    val remoteStorage: Boolean,
)