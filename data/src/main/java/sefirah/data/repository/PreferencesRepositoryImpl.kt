package sefirah.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val IMAGE_CLIPBOARD = booleanPreferencesKey("autoImageClipboard")
        val STORAGE_LOCATION = stringPreferencesKey("storageLocation")
        val APP_ENTRY = booleanPreferencesKey("appEntry")
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

    override suspend fun saveImageClipboardSettings(clipboardSettings: Boolean) {
        datastore.edit {
            it[PreferencesKeys.IMAGE_CLIPBOARD] = clipboardSettings
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
            val imageClipboard = preferences[PreferencesKeys.IMAGE_CLIPBOARD] ?: true
            val storageLocation = preferences[PreferencesKeys.STORAGE_LOCATION] ?: ""
            PreferencesSettings(discovery, imageClipboard, storageLocation)
        }
    }

    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appPreferences")
    }
}