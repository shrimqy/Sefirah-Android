package sefirah.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sefirah.domain.model.DevicePreferences
import sefirah.domain.interfaces.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    context: Context
) : PreferencesRepository {

    private val datastore = context.dataStore

    override suspend fun readAppEntry(): Boolean {
        return datastore.data.map { preferences ->
            preferences[APP_ENTRY] == true
        }.first()
    }

    override suspend fun saveAppEntry() {
        APP_ENTRY.update(true)
    }

    override suspend fun saveTrustAllNetworks(enabled: Boolean) {
        TRUST_ALL_NETWORKS.update(enabled)
    }

    override fun readTrustAllNetworks(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[TRUST_ALL_NETWORKS] != false
        }
    }

    override suspend fun updateStorageLocation(uri: String) {
        STORAGE_LOCATION.update(uri)
    }

    override suspend fun getStorageLocation(): Flow<String> {
        return datastore.data.map { preferences ->
            preferences[STORAGE_LOCATION] ?: ""
        }
    }

    override suspend fun saveLanguage(language: String) {
        LANGUAGE.update(language)
    }

    override fun readLanguage(): Flow<String> {
        return datastore.data.map { preferences ->
            preferences[LANGUAGE] ?: "system"
        }
    }

    override suspend fun savePermissionRequested(permission: String) {
        permissionRequestedKey(permission).update(true)
    }

    override suspend fun clearPermissionRequested(permission: String) {
        permissionRequestedKey(permission).update(false)
    }

    override fun hasRequestedPermission(permission: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[permissionRequestedKey(permission)] == true
        }
    }

    override suspend fun savePassiveDiscovery(enabled: Boolean) {
        datastore.edit {
            it[PASSIVE_DISCOVERY] = enabled
        }
    }

    override fun readPassiveDiscoverySettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PASSIVE_DISCOVERY] != false
        }
    }

    override suspend fun saveLastCheckedForUpdate(lastChecked: Long) {
        LAST_CHECKED_FOR_UPDATE.update(lastChecked)
    }

    override fun readLastCheckedForUpdate(): Flow<Long> {
        return datastore.data.map { preferences ->
            preferences[LAST_CHECKED_FOR_UPDATE] ?: 0
        }
    }

    override suspend fun saveClipboardSyncSettingsForDevice(deviceId: String, clipboardSync: Boolean) {
        deviceClipboardSyncKey(deviceId).update(clipboardSync)
    }

    override fun readClipboardSyncSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceClipboardSyncKey(deviceId)] != false
        }
    }

    override suspend fun saveMessageSyncSettingsForDevice(deviceId: String, messageSync: Boolean) {
        deviceMessageSyncKey(deviceId).update(messageSync)
    }

    override fun readMessageSyncSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceMessageSyncKey(deviceId)] != false
        }
    }

    override suspend fun saveNotificationSyncSettingsForDevice(deviceId: String, notificationSync: Boolean) {
        deviceNotificationSyncKey(deviceId).update(notificationSync)
    }

    override fun readNotificationSyncSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceNotificationSyncKey(deviceId)] != false
        }
    }

    override suspend fun saveCallStateSyncSettingsForDevice(deviceId: String, callStateSync: Boolean) {
        deviceCallStateSyncKey(deviceId).update(callStateSync)
    }

    override fun readCallStateSyncSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceCallStateSyncKey(deviceId)] != false
        }
    }

    override suspend fun saveImageClipboardSettingsForDevice(deviceId: String, copyImagesToClipboard: Boolean) {
        deviceImageClipboardKey(deviceId).update(copyImagesToClipboard)
    }

    override fun readImageClipboardSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceImageClipboardKey(deviceId)] != false
        }
    }

    override suspend fun saveMediaSessionSettingsForDevice(deviceId: String, showMediaSession: Boolean) {
        deviceMediaSessionKey(deviceId).update(showMediaSession)
    }

    override fun readMediaSessionSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceMediaSessionKey(deviceId)] != false
        }
    }

    override suspend fun saveMediaPlayerControlSettingsForDevice(deviceId: String, enabled: Boolean) {
        deviceMediaPlayerControlKey(deviceId).update(enabled)
    }

    override fun readMediaPlayerControlSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceMediaPlayerControlKey(deviceId)] != false
        }
    }

    override suspend fun saveRemoteStorageSettingsForDevice(deviceId: String, enabled: Boolean) {
        deviceRemoteStorageKey(deviceId).update(enabled)
    }

    override fun readRemoteStorageSettingsForDevice(deviceId: String): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[deviceRemoteStorageKey(deviceId)] != false
        }
    }

    private suspend inline fun <T> Preferences.Key<T>.update(newValue: T) {
        datastore.edit { preferences ->
            preferences[this] = newValue
        }
    }

    override fun preferenceSettings(deviceId: String): Flow<DevicePreferences>  {
        return datastore.data.catch {
            emit(emptyPreferences())
        }.map { preferences->
            DevicePreferences(
                clipboardSync = preferences[deviceClipboardSyncKey(deviceId)] != false,
                messageSync = preferences[deviceMessageSyncKey(deviceId)] != false,
                notificationSync = preferences[deviceNotificationSyncKey(deviceId)] != false,
                callStateSync = preferences[deviceCallStateSyncKey(deviceId)] != false,
                imageClipboard = preferences[deviceImageClipboardKey(deviceId)] != false,
                mediaSession = preferences[deviceMediaSessionKey(deviceId)] != false,
                mediaPlayerControl = preferences[deviceMediaPlayerControlKey(deviceId)] != false,
                remoteStorage = preferences[deviceRemoteStorageKey(deviceId)] != false
            )
        }
    }

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appPreferences")

        val LANGUAGE = stringPreferencesKey("language")

        val STORAGE_LOCATION = stringPreferencesKey("storageLocation")
        val APP_ENTRY = booleanPreferencesKey("appEntry")
        val PASSIVE_DISCOVERY = booleanPreferencesKey("passiveDiscovery")
        val LAST_CHECKED_FOR_UPDATE = longPreferencesKey("lastCheckedForUpdate")
        val TRUST_ALL_NETWORKS = booleanPreferencesKey("trustAllNetworks")

        fun permissionRequestedKey(permission: String) = booleanPreferencesKey("permission_requested_$permission")
        fun deviceClipboardSyncKey(deviceId: String) = booleanPreferencesKey("clipboardSync_$deviceId")
        fun deviceMessageSyncKey(deviceId: String) = booleanPreferencesKey("messageSync_$deviceId")
        fun deviceNotificationSyncKey(deviceId: String) = booleanPreferencesKey("notificationSync_$deviceId")
        fun deviceCallStateSyncKey(deviceId: String) = booleanPreferencesKey("callStateSync_$deviceId")
        fun deviceImageClipboardKey(deviceId: String) = booleanPreferencesKey("imageClipboard_$deviceId")
        fun deviceMediaSessionKey(deviceId: String) = booleanPreferencesKey("mediaSession_$deviceId")
        fun deviceMediaPlayerControlKey(deviceId: String) = booleanPreferencesKey("mediaPlayerControl_$deviceId")
        fun deviceRemoteStorageKey(deviceId: String) = booleanPreferencesKey("remoteStorage_$deviceId")
    }
}