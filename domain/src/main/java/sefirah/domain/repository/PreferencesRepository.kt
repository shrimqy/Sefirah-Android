package sefirah.domain.repository

import kotlinx.coroutines.flow.Flow
import sefirah.domain.model.PreferencesSettings

interface PreferencesRepository {
    
    suspend fun readAppEntry(): Flow<Boolean>
    suspend fun saveAppEntry()

    suspend fun saveSynStatus(syncStatus: Boolean)
    fun readSyncStatus(): Flow<Boolean>

    suspend fun saveLastConnected(hostAddress: String)
    fun readLastConnected(): Flow<String?>

    suspend fun saveAutoDiscoverySettings(discoverySettings: Boolean)
    suspend fun saveImageClipboardSettings(clipboardSettings: Boolean)
    suspend fun updateStorageLocation(uri: String)
    suspend fun getStorageLocation(): Flow<String>
    fun preferenceSettings(): Flow<PreferencesSettings>
}

