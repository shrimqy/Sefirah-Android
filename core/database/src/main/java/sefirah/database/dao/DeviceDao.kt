package sefirah.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.PairedDeviceEntity
import sefirah.domain.model.AddressEntry

@Dao
interface DeviceDao {
    @Query("SELECT * FROM PairedDeviceEntity")
    suspend fun getAllDevices(): List<PairedDeviceEntity>

    @Query("SELECT * FROM PairedDeviceEntity")
    fun getAllDevicesFlow(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM PairedDeviceEntity WHERE deviceId = :deviceId")
    fun getRemoteDevice(deviceId: String): Flow<PairedDeviceEntity?>

    @Query("DELETE FROM PairedDeviceEntity WHERE deviceId = :deviceId")
    suspend fun removeDevice(deviceId: String)

    @Update
    suspend fun updateDevice(device: PairedDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDevice(device: PairedDeviceEntity)

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

    @Query("UPDATE PairedDeviceEntity SET addresses = :addresses WHERE deviceId = :deviceId")
    suspend fun updateDeviceAddresses(deviceId: String, addresses: List<AddressEntry>)
}