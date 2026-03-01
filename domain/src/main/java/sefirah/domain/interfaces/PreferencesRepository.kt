package sefirah.domain.interfaces

import kotlinx.coroutines.flow.Flow
import sefirah.domain.model.DevicePreferences

interface PreferencesRepository {
    fun preferenceSettings(deviceId: String): Flow<DevicePreferences>

    suspend fun readAppEntry(): Boolean
    suspend fun saveAppEntry()

    suspend fun saveLanguage(language: String)
    fun readLanguage(): Flow<String>

    suspend fun saveTrustAllNetworks(enabled: Boolean)
    fun readTrustAllNetworks(): Flow<Boolean>

    suspend fun updateStorageLocation(uri: String)
    suspend fun getStorageLocation(): Flow<String>

    suspend fun savePermissionRequested(permission: String)
    suspend fun clearPermissionRequested(permission: String)
    fun hasRequestedPermission(permission: String): Flow<Boolean>

    suspend fun savePassiveDiscovery(enabled: Boolean)
    fun readPassiveDiscoverySettings(): Flow<Boolean>

    suspend fun saveLastCheckedForUpdate(lastChecked: Long)
    fun readLastCheckedForUpdate(): Flow<Long>

    // Device-specific settings
    suspend fun saveClipboardSyncSettingsForDevice(deviceId: String, clipboardSync: Boolean)
    fun readClipboardSyncSettingsForDevice(deviceId: String): Flow<Boolean>
    
    suspend fun saveMessageSyncSettingsForDevice(deviceId: String, messageSync: Boolean)
    fun readMessageSyncSettingsForDevice(deviceId: String): Flow<Boolean>
    
    suspend fun saveNotificationSyncSettingsForDevice(deviceId: String, notificationSync: Boolean)
    fun readNotificationSyncSettingsForDevice(deviceId: String): Flow<Boolean>

    suspend fun saveCallStateSyncSettingsForDevice(deviceId: String, callStateSync: Boolean)
    fun readCallStateSyncSettingsForDevice(deviceId: String): Flow<Boolean>

    suspend fun saveImageClipboardSettingsForDevice(deviceId: String, copyImagesToClipboard: Boolean)
    fun readImageClipboardSettingsForDevice(deviceId: String): Flow<Boolean>
    
    suspend fun saveMediaSessionSettingsForDevice(deviceId: String, showMediaSession: Boolean)
    fun readMediaSessionSettingsForDevice(deviceId: String): Flow<Boolean>

    suspend fun saveMediaPlayerControlSettingsForDevice(deviceId: String, enabled: Boolean)
    fun readMediaPlayerControlSettingsForDevice(deviceId: String): Flow<Boolean>

    suspend fun saveRemoteStorageSettingsForDevice(deviceId: String, enabled: Boolean)
    fun readRemoteStorageSettingsForDevice(deviceId: String): Flow<Boolean>
}

