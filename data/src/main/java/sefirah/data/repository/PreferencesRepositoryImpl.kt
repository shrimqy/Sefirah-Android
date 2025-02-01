package sefirah.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sefirah.domain.model.PreferencesSettings
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

class PreferencesDatastore @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {

    private object PreferencesKeys{
        val SYNC_STATUS = booleanPreferencesKey("syncStatus")
        val LAST_CONNECTED = stringPreferencesKey("lastConnected")
        val AUTO_DISCOVERY = booleanPreferencesKey("autoDiscovery")
        val CLIPBOARD_SYNC = booleanPreferencesKey("clipboardSync")
        val READ_SENSITIVE_NOTIFICATIONS = booleanPreferencesKey("readSensitiveNotifications")
        val MEDIA_SESSION = booleanPreferencesKey("mediaSession")
        val IMAGE_CLIPBOARD = booleanPreferencesKey("autoImageClipboard")
        val STORAGE_LOCATION = stringPreferencesKey("storageLocation")
        val APP_ENTRY = booleanPreferencesKey("appEntry")
        val PERMISSION_REQUESTED = stringPreferencesKey("permission_requested")
        val NOTIFICATION_SYNC = booleanPreferencesKey("notificationSync")
        val REMOTE_STORAGE = booleanPreferencesKey("remoteStorage")
        val PASSIVE_DISCOVERY = booleanPreferencesKey("passiveDiscovery")
    }

    private val datastore = context.dataStore

    override suspend fun readAppEntry(): Flow<Boolean> {
        return datastore.data.map { status->
            status[PreferencesKeys.APP_ENTRY] ?: false
        }
    }

    override suspend fun saveAppEntry() {
        datastore.edit { settings ->
            settings[PreferencesKeys.APP_ENTRY] = true
        }
    }

    override suspend fun saveSynStatus(syncStatus: Boolean) {
        datastore.edit { status->
            status[PreferencesKeys.SYNC_STATUS] = syncStatus
        }
    }

    override fun readSyncStatus(): Flow<Boolean> {
        return datastore.data.map { status ->
            status[PreferencesKeys.SYNC_STATUS] ?: false
        }
    }

    override suspend fun saveLastConnected(hostAddress: String) {
        datastore.edit { status->
            status[PreferencesKeys.LAST_CONNECTED] = hostAddress
        }
    }

    override fun readLastConnected(): Flow<String?> {
        return datastore.data.map { host ->
            host[PreferencesKeys.LAST_CONNECTED]
        }
    }

    override suspend fun saveAutoDiscoverySettings(discoverySettings: Boolean) {
        datastore.edit {
            it[PreferencesKeys.AUTO_DISCOVERY] = discoverySettings
        }
    }

    override suspend fun saveClipboardSyncSettings(clipboardSync: Boolean) {
        datastore.edit {
            it[PreferencesKeys.CLIPBOARD_SYNC] = clipboardSync
        }
    }

    override fun readClipboardSyncSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.CLIPBOARD_SYNC] ?: true
        }
    }

//    override suspend fun saveReadSensitiveNotificationsSettings(readSensitiveNotifications: Boolean) {
//        datastore.edit {
//            it[PreferencesKeys.READ_SENSITIVE_NOTIFICATIONS] = readSensitiveNotifications
//        }
//    }

//    override fun readReadSensitiveNotificationsSettings(): Flow<Boolean> {
//        return datastore.data.map { preferences ->
//            preferences[PreferencesKeys.READ_SENSITIVE_NOTIFICATIONS] ?: false
//        }
//    }

    

    override suspend fun saveNotificationSyncSettings(notificationSync: Boolean) {
        datastore.edit {
            it[PreferencesKeys.NOTIFICATION_SYNC] = notificationSync
        }
    }

    override fun readNotificationSyncSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_SYNC] ?: true
        }
    }

    override suspend fun saveMediaSessionSettings(showMediaSession: Boolean) {
        datastore.edit {
            it[PreferencesKeys.MEDIA_SESSION] = showMediaSession
        }
    }

    override fun readMediaSessionSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.MEDIA_SESSION] ?: true
        }
    }

    override suspend fun saveImageClipboardSettings(copyImagesToClipboard: Boolean) {
        datastore.edit {
            it[PreferencesKeys.IMAGE_CLIPBOARD] = copyImagesToClipboard
        }
    }

    override fun readImageClipboardSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.IMAGE_CLIPBOARD] ?: true
        }
    }

    override suspend fun updateStorageLocation(uri: String) {
        datastore.edit {
            it[PreferencesKeys.STORAGE_LOCATION] = uri
        }
    }

    override suspend fun getStorageLocation(): Flow<String> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.STORAGE_LOCATION] ?: ""
        }
    }


    override fun preferenceSettings(): Flow<PreferencesSettings>  {
        return datastore.data.catch {
            emit(emptyPreferences())
        }.map { preferences->
            val discovery = preferences[PreferencesKeys.AUTO_DISCOVERY] ?: true
            val storageLocation = preferences[PreferencesKeys.STORAGE_LOCATION] ?: ""
            val readSensitiveNotifications = preferences[PreferencesKeys.READ_SENSITIVE_NOTIFICATIONS] ?: false
            val notificationSync =  preferences[PreferencesKeys.NOTIFICATION_SYNC] ?: true
            val imageClipboard = preferences[PreferencesKeys.IMAGE_CLIPBOARD] ?: true
            val clipboardSync = preferences[PreferencesKeys.CLIPBOARD_SYNC] ?: true
            val mediaSession = preferences[PreferencesKeys.MEDIA_SESSION] ?: true
            val remoteStorage = preferences[PreferencesKeys.REMOTE_STORAGE] ?: true
            PreferencesSettings(
                autoDiscovery =  discovery,
                storageLocation =  storageLocation,
                readSensitiveNotifications = readSensitiveNotifications,
                notificationSync = notificationSync,
                mediaSession =  mediaSession,
                clipboardSync =  clipboardSync,
                imageClipboard =  imageClipboard,
                remoteStorage = remoteStorage
            )
        }
    }

    override suspend fun savePermissionRequested(permission: String) {
        datastore.edit { prefs ->
            val requested = prefs[PreferencesKeys.PERMISSION_REQUESTED] ?: ""
            if (!requested.contains(permission)) {
                prefs[PreferencesKeys.PERMISSION_REQUESTED] = "$requested,$permission"
            }
        }
    }

    override fun hasRequestedPermission(permission: String): Flow<Boolean> {
        return datastore.data.map { prefs ->
            val requested = prefs[PreferencesKeys.PERMISSION_REQUESTED] ?: ""
            requested.split(",").contains(permission)
        }
    }

    override suspend fun saveRemoteStorageSettings(enabled: Boolean) {
        datastore.edit {
            it[PreferencesKeys.REMOTE_STORAGE] = enabled
        }
    }

    override fun readRemoteStorageSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.REMOTE_STORAGE] ?: true
        }
    }

    override suspend fun savePassiveDiscovery(enabled: Boolean) {
        datastore.edit {
            it[PreferencesKeys.PASSIVE_DISCOVERY] = enabled
        }
    }

    override fun readPassiveDiscoverySettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PreferencesKeys.PASSIVE_DISCOVERY] ?: true
        }
    }

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appPreferences")
    }
}