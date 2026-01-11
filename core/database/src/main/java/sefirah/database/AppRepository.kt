package sefirah.database

import android.content.Context
import sefirah.database.dao.NetworkDao
import sefirah.database.dao.DeviceDao
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.NetworkEntity
import sefirah.database.model.PairedDeviceEntity
import sefirah.database.model.toDomain
import sefirah.domain.model.AddressEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val context: Context,
    private val deviceDao: DeviceDao,
    private val networkDao: NetworkDao
){
    fun getAllDevicesFlow() = deviceDao.getAllDevicesFlow()
    suspend fun addDevice(device: PairedDeviceEntity) = deviceDao.addDevice(device)
    suspend fun removeDevice(deviceId: String) = deviceDao.removeDevice(deviceId)
    suspend fun updateDevice(device: PairedDeviceEntity) = deviceDao.updateDevice(device)
    suspend fun updateDeviceAddresses(deviceId: String, ipAddresses: List<AddressEntry>) = deviceDao.updateDeviceAddresses(deviceId, ipAddresses)

    fun getRemoteDevice(deviceId: String) = deviceDao.getRemoteDevice(deviceId)

    suspend fun addLocalDevice(device: LocalDeviceEntity) = deviceDao.addLocalDevice(device)
    suspend fun updateLocalDeviceName(deviceId: String, deviceName: String) = deviceDao.updateLocalDeviceName(deviceId, deviceName)
    fun getLocalDevice() = deviceDao.getLocalDevice()
    fun getLocalDeviceFlow() = deviceDao.getLocalDeviceFlow()

    fun getAllNetworksFlow() = networkDao.getAllNetworksFlow()
    suspend fun addNetwork(network: NetworkEntity) = networkDao.addNetwork(network)
    fun getNetwork(ssid: String) = networkDao.getNetwork(ssid)

    suspend fun deleteNetwork(network: NetworkEntity) = networkDao.deleteNetwork(network)
    suspend fun updateNetwork(network: NetworkEntity) = networkDao.updateNetwork(network)
}