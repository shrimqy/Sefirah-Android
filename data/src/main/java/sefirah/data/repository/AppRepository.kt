package sefirah.data.repository

import android.content.Context
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import sefirah.database.dao.NetworkDao
import sefirah.database.AppDatabase
import sefirah.database.dao.DeviceDao
import sefirah.database.model.DeviceNetworkCrossRef
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.NetworkEntity
import sefirah.database.model.RemoteDeviceEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val db: AppDatabase,
    @ApplicationContext private val context: Context,
    private val deviceDao: DeviceDao,
    private val networkDao: NetworkDao
){
    fun getAllDevicesFlow() = deviceDao.getAllDevicesFlow()
    suspend fun addDevice(device: RemoteDeviceEntity) = deviceDao.addDevice(device)
    suspend fun removeDevice(deviceId: String) = deviceDao.removeDevice(deviceId)
    suspend fun updateDevice(device: RemoteDeviceEntity) = deviceDao.updateDevice(device)
    suspend fun updatePreferredIp(deviceId: String, preferredIp: String) = deviceDao.updatePreferredIp(deviceId, preferredIp)
    suspend fun updateIpAddresses(deviceId: String, ipAddresses: List<String>) = deviceDao.updateIpAddresses(deviceId, ipAddresses)
    fun getLastConnectedDevice() = deviceDao.getLastConnectedDevice()

    fun getLastConnectedDeviceFlow() = deviceDao.getLastConnectedDeviceFlow()

    fun getRemoteDevice(deviceId: String) = deviceDao.getRemoteDevice(deviceId)

    suspend fun addLocalDevice(device: LocalDeviceEntity) = deviceDao.addLocalDevice(device)
    suspend fun updateLocalDevice(device: LocalDeviceEntity) = deviceDao.updateLocalDevice(device)
    suspend fun getLocalDevice() = deviceDao.getLocalDevice()

    fun getAllNetworksFlow() = networkDao.getAllNetworksFlow()
    suspend fun addNetwork(network: NetworkEntity) = networkDao.addNetwork(network)
    fun getNetwork(ssid: String) = networkDao.getNetwork(ssid)
    suspend fun deleteNetwork(network: NetworkEntity) = networkDao.deleteNetwork(network)
    suspend fun updateNetwork(network: NetworkEntity) = networkDao.updateNetwork(network)

    suspend fun addNetworkToDevice(crossRef: DeviceNetworkCrossRef) = deviceDao.addNetworkToDevice(crossRef)
    suspend fun removeNetworkFromDevice(deviceId: String, ssid: String) = deviceDao.removeNetworkFromDevice(deviceId, ssid)
    suspend fun removeAllNetworksFromDevice(deviceId: String) = deviceDao.removeAllNetworksFromDevice(deviceId)
    fun getNetworksForDevice(deviceId: String) = deviceDao.getNetworksForDevice(deviceId)
}