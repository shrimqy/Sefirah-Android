package sefirah.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import sefirah.database.model.NetworkEntity

@Dao
interface NetworkDao {
    @Query("SELECT * FROM NetworkEntity")
    suspend fun getAllNetworks(): List<NetworkEntity>

    @Query("SELECT * FROM NetworkEntity")
    fun getAllNetworksFlow(): Flow<List<NetworkEntity>>

    @Query("SELECT * FROM NetworkEntity WHERE ssid = :ssid")
    fun getNetwork(ssid: String): Flow<NetworkEntity>?

    @Query("DELETE FROM NetworkEntity WHERE ssid = :ssid")
    suspend fun removeNetwork(ssid: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNetwork(network: NetworkEntity)
}