package sefirah.domain.repository

import kotlinx.coroutines.flow.Flow
import sefirah.domain.model.DiscoveryMode
import sefirah.domain.model.PreferencesSettings

interface PreferencesRepository {
    fun preferenceSettings(): Flow<PreferencesSettings>

    suspend fun readAppEntry(): Boolean
    suspend fun saveAppEntry()

    suspend fun saveLanguage(language: String)
    fun readLanguage(): Flow<String>

    suspend fun saveSynStatus(syncStatus: Boolean)
    fun readSyncStatus(): Flow<Boolean>


    suspend fun saveLastConnected(hostAddress: String)
    fun readLastConnected(): Flow<String?>

    suspend fun saveDiscoveryMode(discoveryMode: DiscoveryMode)
    suspend fun readDiscoveryMode(): DiscoveryMode
    fun readDiscoveryModeFlow(): Flow<DiscoveryMode>

    suspend fun updateStorageLocation(uri: String)
    suspend fun getStorageLocation(): Flow<String>


    suspend fun savePermissionRequested(permission: String)
    fun hasRequestedPermission(permission: String): Flow<Boolean>

    suspend fun saveMediaSessionSettings(showMediaSession: Boolean)
    fun readMediaSessionSettings(): Flow<Boolean>

    suspend fun saveClipboardSyncSettings(clipboardSync: Boolean)
    fun readClipboardSyncSettings(): Flow<Boolean>

    suspend fun saveImageClipboardSettings(copyImagesToClipboard: Boolean)
    fun readImageClipboardSettings(): Flow<Boolean>

//    suspend fun saveReadSensitiveNotificationsSettings(readSensitiveNotifications: Boolean)
//    fun readReadSensitiveNotificationsSettings(): Flow<Boolean>

    suspend fun saveNotificationSyncSettings(notificationSync: Boolean)
    fun readNotificationSyncSettings(): Flow<Boolean>

    suspend fun saveRemoteStorageSettings(enabled: Boolean)
    fun readRemoteStorageSettings(): Flow<Boolean>

    suspend fun savePassiveDiscovery(enabled: Boolean)
    fun readPassiveDiscoverySettings(): Flow<Boolean>

    suspend fun saveMessageSyncSettings(messageSync: Boolean)
    fun readMessageSyncSettings(): Flow<Boolean>

    suspend fun saveLastCheckedForUpdate(lastChecked: Long)
    fun readLastCheckedForUpdate(): Flow<Long>
}

