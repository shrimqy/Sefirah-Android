package sefirah.domain.model

data class PreferencesSettings( 
    val language: String,
    val autoDiscovery: Boolean,
    val storageLocation: String,
    val readSensitiveNotifications: Boolean,
    val notificationSync: Boolean,
    val mediaSession: Boolean,
    val clipboardSync: Boolean,
    val imageClipboard: Boolean,
    val remoteStorage: Boolean
)