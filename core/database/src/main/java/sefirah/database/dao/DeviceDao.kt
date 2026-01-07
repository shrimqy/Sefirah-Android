package sefirah.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.RemoteDeviceEntity
import sefirah.domain.model.AddressEntry

@Dao
interface DeviceDao {
    @Query("SELECT * FROM RemoteDeviceEntity")
    suspend fun getAllDevices(): List<RemoteDeviceEntity>

    @Query("SELECT * FROM RemoteDeviceEntity")
    fun getAllDevicesFlow(): Flow<List<RemoteDeviceEntity>>

    @Query("SELECT * FROM RemoteDeviceEntity WHERE deviceId = :deviceId")
    fun getRemoteDevice(deviceId: String): Flow<RemoteDeviceEntity?>

    @Query("SELECT * FROM RemoteDeviceEntity ORDER BY lastConnected DESC LIMIT 1")
    fun getLastConnectedDevice(): RemoteDeviceEntity?

    @Query("SELECT * FROM RemoteDeviceEntity ORDER BY lastConnected DESC LIMIT 1")
    fun getLastConnectedDeviceFlow(): Flow<RemoteDeviceEntity?>

    @Query("DELETE FROM REMOTEDEVICEENTITY WHERE deviceId = :deviceId")
    suspend fun removeDevice(deviceId: String)

    @Update
    suspend fun updateDevice(device: RemoteDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDevice(device: RemoteDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLocalDevice(device: LocalDeviceEntity)

    @Update
    suspend fun updateLocalDevice(device: LocalDeviceEntity)

    @Query("UPDATE LocalDeviceEntity SET deviceName = :deviceName WHERE deviceId = :deviceId")
    suspend fun updateLocalDeviceName(deviceId: String, deviceName: String)

    @Query("SELECT * FROM LocalDeviceEntity LIMIT 1")
    fun getLocalDevice(): LocalDeviceEntity?

    @Query("SELECT * FROM LocalDeviceEntity LIMIT 1")
    fun getLocalDeviceFlow(): Flow<LocalDeviceEntity?>

    @Query("UPDATE RemoteDeviceEntity SET addresses = :addresses WHERE deviceId = :deviceId")
    suspend fun updateDeviceAddresses(deviceId: String, addresses: List<AddressEntry>)
}