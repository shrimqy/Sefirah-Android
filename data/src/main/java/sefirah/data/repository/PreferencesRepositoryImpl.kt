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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sefirah.domain.model.PreferencesSettings
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

class PreferencesDatastore @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {

    private val datastore = context.dataStore

    override suspend fun readAppEntry(): Boolean {
        return datastore.data.map { preferences ->
            preferences[APP_ENTRY] ?: false
        }.first()
    }

    override suspend fun saveAppEntry() {
        APP_ENTRY.update(true)
    }


    override suspend fun saveSynStatus(syncStatus: Boolean) {
        SYNC_STATUS.update(syncStatus)
    }


    override fun readSyncStatus(): Flow<Boolean> {
        return datastore.data.map { status ->
            status[SYNC_STATUS] ?: false
        }
    }


    override suspend fun saveLastConnected(hostAddress: String) {
        LAST_CONNECTED.update(hostAddress)
    }


    override fun readLastConnected(): Flow<String?> {
        return datastore.data.map { host ->
            host[LAST_CONNECTED]
        }
    }

    override suspend fun readAutoDiscoverySettings(): Boolean {
        return datastore.data.map { preferences ->
            preferences[AUTO_DISCOVERY] ?: true
        }.first()
    }


    override suspend fun saveAutoDiscoverySettings(discoverySettings: Boolean) {
        AUTO_DISCOVERY.update(discoverySettings)
    }


    override suspend fun saveClipboardSyncSettings(clipboardSync: Boolean) {
        CLIPBOARD_SYNC.update(clipboardSync)
    }


    override fun readClipboardSyncSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[CLIPBOARD_SYNC] ?: true
        }
    }

    override suspend fun saveNotificationSyncSettings(notificationSync: Boolean) {
        NOTIFICATION_SYNC.update(notificationSync)
    }

    override fun readNotificationSyncSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[NOTIFICATION_SYNC] ?: true
        }
    }

    override suspend fun saveMediaSessionSettings(showMediaSession: Boolean) {
        MEDIA_SESSION.update(showMediaSession)
    }

    override fun readMediaSessionSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[MEDIA_SESSION] ?: true
        }
    }

    override suspend fun saveImageClipboardSettings(copyImagesToClipboard: Boolean) {
        IMAGE_CLIPBOARD.update(copyImagesToClipboard)
    }


    override fun readImageClipboardSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[IMAGE_CLIPBOARD] ?: true
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

    override suspend fun saveMessageSyncSettings(messageSync: Boolean) {
        MESSAGE_SYNC.update(messageSync)
    }

    override fun readMessageSyncSettings(): Flow<Boolean> { 
        return datastore.data.map { preferences ->
            preferences[MESSAGE_SYNC] ?: true
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
        datastore.edit { prefs ->
            val requested = prefs[PERMISSION_REQUESTED] ?: ""
            if (!requested.contains(permission)) {
                prefs[PERMISSION_REQUESTED] = "$requested,$permission"
            }
        }
    }

    override fun hasRequestedPermission(permission: String): Flow<Boolean> {
        return datastore.data.map { prefs ->
            val requested = prefs[PERMISSION_REQUESTED] ?: ""
            requested.split(",").contains(permission)
        }
    }

    override suspend fun saveRemoteStorageSettings(enabled: Boolean) {
        REMOTE_STORAGE.update(enabled)
    }

    override fun readRemoteStorageSettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[REMOTE_STORAGE] ?: true
        }
    }

    override suspend fun savePassiveDiscovery(enabled: Boolean) {
        datastore.edit {
            it[PASSIVE_DISCOVERY] = enabled
        }
    }

    override fun readPassiveDiscoverySettings(): Flow<Boolean> {
        return datastore.data.map { preferences ->
            preferences[PASSIVE_DISCOVERY] ?: true
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

    private suspend inline fun <T> Preferences.Key<T>.update(newValue: T) {
        datastore.edit { preferences ->
            preferences[this] = newValue
        }
    }

    override fun preferenceSettings(): Flow<PreferencesSettings>  {
        return datastore.data.catch {
            emit(emptyPreferences())
        }.map { preferences->
            val language = preferences[LANGUAGE] ?: "system"
            val discovery = preferences[AUTO_DISCOVERY] ?: true
            val storageLocation = preferences[STORAGE_LOCATION] ?: ""
            val readSensitiveNotifications = preferences[READ_SENSITIVE_NOTIFICATIONS] ?: false
            val notificationSync =  preferences[NOTIFICATION_SYNC] ?: true
            val imageClipboard = preferences[IMAGE_CLIPBOARD] ?: true
            val messageSync = preferences[MESSAGE_SYNC] ?: true
            val clipboardSync = preferences[CLIPBOARD_SYNC] ?: true
            val mediaSession = preferences[MEDIA_SESSION] ?: true
            val remoteStorage = preferences[REMOTE_STORAGE] ?: true

            PreferencesSettings(
                language = language,
                autoDiscovery =  discovery,
                storageLocation =  storageLocation,
                readSensitiveNotifications = readSensitiveNotifications,
                notificationSync = notificationSync,
                mediaSession =  mediaSession,
                clipboardSync =  clipboardSync,
                messageSync = messageSync,
                imageClipboard =  imageClipboard,
                remoteStorage = remoteStorage
            )
        }
    }

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appPreferences")

        val LANGUAGE = stringPreferencesKey("language")
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
        val MESSAGE_SYNC = booleanPreferencesKey("messageSync")
        val LAST_CHECKED_FOR_UPDATE = longPreferencesKey("lastCheckedForUpdate")
    }
}