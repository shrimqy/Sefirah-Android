package sefirah.domain.model

data class PreferencesSettings(
    val autoDiscovery: Boolean,
    val imageClipboard: Boolean,
    val storageLocation: String,
)