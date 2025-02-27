package sefirah.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.RemoteDeviceEntity
import sefirah.database.model.DeviceNetworkCrossRef
import sefirah.database.model.NetworkEntity
import sefirah.database.model.DeviceWithNetworks
import sefirah.database.model.CustomIpEntity
import sefirah.database.model.DeviceCustomIpCrossRef

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
    suspend fun getLocalDevice(): LocalDeviceEntity

    @Query("SELECT * FROM LocalDeviceEntity LIMIT 1")
    fun getLocalDeviceFlow(): Flow<LocalDeviceEntity?>

    @Query("UPDATE RemoteDeviceEntity SET prefAddress = :preferredIp WHERE deviceId = :deviceId")
    suspend fun updatePreferredIp(deviceId: String, preferredIp: String)

    @Query("UPDATE RemoteDeviceEntity SET ipAddresses = :ipAddresses WHERE deviceId = :deviceId")
    suspend fun updateIpAddresses(deviceId: String, ipAddresses: List<String>)

    @Transaction
    @Query("SELECT * FROM RemoteDeviceEntity WHERE deviceId = :deviceId")
    fun getDeviceWithNetworks(deviceId: String): Flow<DeviceWithNetworks?>

    @Transaction
    @Query("SELECT * FROM RemoteDeviceEntity")
    fun getAllDevicesWithNetworks(): Flow<List<DeviceWithNetworks>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addNetworkToDevice(crossRef: DeviceNetworkCrossRef)

    @Query("DELETE FROM DeviceNetworkCrossRef WHERE deviceId = :deviceId AND ssid = :ssid")
    suspend fun removeNetworkFromDevice(deviceId: String, ssid: String)

    @Query("DELETE FROM DeviceNetworkCrossRef WHERE deviceId = :deviceId")
    suspend fun removeAllNetworksFromDevice(deviceId: String)

    @Query("SELECT n.* FROM NetworkEntity n INNER JOIN DeviceNetworkCrossRef ref ON n.ssid = ref.ssid WHERE ref.deviceId = :deviceId")
    fun getNetworksForDevice(deviceId: String): Flow<List<NetworkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCustomIp(customIp: CustomIpEntity)

    @Delete
    suspend fun deleteCustomIp(ipEntity: CustomIpEntity)

    @Query("SELECT ipAddress FROM CustomIpEntity")
    fun getAllCustomIpFlow(): Flow<List<String>>

    @Query("""
        SELECT c.ipAddress FROM CustomIpEntity c 
        LEFT JOIN DeviceCustomIpCrossRef ref ON c.ipAddress = ref.ipAddress 
        WHERE ref.deviceId IS NULL
    """)
    suspend fun getUnlinkedCustomIps(): List<String>

    @Query("""
        SELECT c.ipAddress FROM CustomIpEntity c 
        INNER JOIN DeviceCustomIpCrossRef ref ON c.ipAddress = ref.ipAddress 
        INNER JOIN RemoteDeviceEntity d ON ref.deviceId = d.deviceId 
        WHERE d.deviceId = (
            SELECT deviceId FROM RemoteDeviceEntity 
            WHERE lastConnected IS NOT NULL 
            ORDER BY lastConnected DESC LIMIT 1
        )
    """)
    suspend fun getCustomIpsForLastConnectedDevice(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkCustomIpToDevice(crossRef: DeviceCustomIpCrossRef)

    @Delete
    suspend fun unlinkCustomIpFromDevice(crossRef: DeviceCustomIpCrossRef)

    @Query("DELETE FROM DeviceCustomIpCrossRef WHERE deviceId = :deviceId")
    suspend fun unlinkAllCustomIpsFromDevice(deviceId: String)
}