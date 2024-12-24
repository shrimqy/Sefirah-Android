package sefirah.data.repository

import android.content.Context
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import sefirah.database.dao.NetworkDao
import sefirah.database.AppDatabase
import sefirah.database.dao.DeviceDao
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
    fun getDevice(ipAddress: String) = deviceDao.getDevice(ipAddress)
    fun getDeviceFlow(ipAddress: String) = deviceDao.getDeviceFlow(ipAddress)
    suspend fun getHostAddress(deviceName: String) = deviceDao.getHostAddress(deviceName)
    suspend fun removeDevice(deviceName: String) = deviceDao.removeDevice(deviceName)
    suspend fun updateDevice(device: RemoteDeviceEntity) = deviceDao.updateDevice(device)
    fun getLastConnectedDevice() = deviceDao.getLastConnectedDevice()

    fun getLastConnectedDeviceFlow() = deviceDao.getLastConnectedDeviceFlow()

    // LocalDevice functions
    suspend fun addLocalDevice(device: LocalDeviceEntity) = deviceDao.addLocalDevice(device)
    suspend fun updateLocalDevice(device: LocalDeviceEntity) = deviceDao.updateLocalDevice(device)
    suspend fun getLocalDevice() = deviceDao.getLocalDevice()

    fun getAllNetworkFlow() = networkDao.getAllNetworksFlow()
    suspend fun addNetwork(network: NetworkEntity) = networkDao.addNetwork(network)
    fun getNetwork(ssid: String) = networkDao.getNetwork(ssid)
    suspend fun removeNetwork(ssid: String) = networkDao.removeNetwork(ssid)
}