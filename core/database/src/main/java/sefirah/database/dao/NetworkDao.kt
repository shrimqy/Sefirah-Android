package sefirah.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import sefirah.database.model.CustomIpEntity
import sefirah.database.model.NetworkEntity

@Dao
interface NetworkDao {
    @Query("SELECT * FROM NetworkEntity")
    suspend fun getAllNetworks(): List<NetworkEntity>

    @Query("SELECT * FROM NetworkEntity")
    fun getAllNetworksFlow(): Flow<List<NetworkEntity>>

    @Query("SELECT * FROM NetworkEntity WHERE ssid = :ssid")
    fun getNetwork(ssid: String): NetworkEntity?

    @Query("SELECT isEnabled FROM NetworkEntity WHERE ssid = :ssid")
    fun isNetworkEnabled(ssid: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNetwork(network: NetworkEntity)

    @Update
    suspend fun updateNetwork(network: NetworkEntity)

    @Delete
    suspend fun deleteNetwork(network: NetworkEntity)
}