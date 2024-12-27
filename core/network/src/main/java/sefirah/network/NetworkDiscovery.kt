package sefirah.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.NetworkEntity
import sefirah.network.NetworkService.Companion.REMOTE_INFO
import javax.inject.Inject


class NetworkDiscovery @Inject constructor(
    private val nsdService: NsdService,
    private val appRepository: AppRepository,
    private val context: Context,
) {
    private lateinit var connectivityManager: ConnectivityManager
    private var isRegistered = false

    fun register() {
        if (isRegistered) return
        
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isRegistered = true
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun unregister() {
        if (!isRegistered) return
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    private val networkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                Log.d(TAG, "Network callback Received")
                val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                startDeviceDiscovery(wifiInfo)
            }
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network callback Received")
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                startDeviceDiscovery(wifiInfo)
            }
        }
    }

    private fun startDeviceDiscovery(wifiInfo: WifiInfo?) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "WifiInfo: ${wifiInfo?.ssid}")
            if (wifiInfo != null && wifiInfo.ssid != "<unknown ssid>") {
                val ssid = wifiInfo.ssid.removeSurrounding("\"")
                val knownNetwork = appRepository.getNetwork(ssid)
                if (knownNetwork != null) {
                    startNSDDiscovery()
                }
            }
        }
    }

    private fun startNSDDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            val lastConnectedDevice = appRepository.getLastConnectedDevice()
            nsdService.startDiscovery()
            nsdService.services.collect { services ->
                if (services.isNotEmpty() && lastConnectedDevice != null) {
                    for (service in services) {
                        if (service.deviceId != lastConnectedDevice.deviceId) break
                        val workRequest = OneTimeWorkRequestBuilder<NetworkWorker>()
                            .setInputData(workDataOf(REMOTE_INFO to lastConnectedDevice.deviceId))
                            .build()
                        WorkManager.getInstance(context).enqueue(workRequest)
                        nsdService.stopDiscovery()
                    }
                }
            }
        }
    }

    suspend fun addCurrentNetworkSSID() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val ssid : String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val wifiInfo = capabilities?.transportInfo as? WifiInfo
            ssid =  wifiInfo?.ssid?.removeSurrounding("\"")
        } else {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            ssid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"")
        }

        if(ssid != null)  {
            Log.d(TAG, "Network ssid Added")
            appRepository.addNetwork(NetworkEntity(ssid))
        }
    }

    companion object {
        private const val TAG = "NetworkDiscovery"
    }
}