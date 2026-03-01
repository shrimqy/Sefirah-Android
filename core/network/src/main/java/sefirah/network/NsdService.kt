package sefirah.network

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sefirah.domain.model.UdpBroadcast
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdService @Inject constructor(val context: Context) {
    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val _services = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val services: StateFlow<List<NsdServiceInfo>> = _services.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var currentServiceID: String? = null

    private var multicastLock: WifiManager.MulticastLock
    private val executor = Executors.newSingleThreadExecutor()

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("SefirahMulticastLock")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring multicast lock", e)
            throw e
        }
    }

    fun startDiscovery() {
        try {
            if (discoveryListener != null) return

            Log.d(TAG, "Starting mDns discovery")
            multicastLock.acquire()
            _services.value = emptyList()
            discoveryListener = createDiscoveryListener()
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error in startDiscovery: ${e.message}", e)
        }
    }

    fun stopDiscovery() {
        try {
            if (discoveryListener == null) return

            nsdManager.stopServiceDiscovery(discoveryListener)
            multicastLock.release()
            discoveryListener = null
            Log.d(TAG, "Stopped service discovery")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error stopping discovery: ${e.message}", e)
        }
    }

    fun advertiseService(broadcast: UdpBroadcast, port: Int) {
        try {            
            if (registrationListener != null) {
                stopAdvertisingService()
            }
            
            currentServiceID = broadcast.deviceId

            val serviceInfo = NsdServiceInfo().also {
                it.serviceType = SERVICE_TYPE
                it.serviceName = currentServiceID
                it.port = port
            }
            serviceInfo.setAttribute("deviceName", broadcast.deviceName)

            registrationListener = createRegistrationListener()
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error advertising service: ${e.message}")
        }
    }

    fun stopAdvertisingService() {
        try {
            if (registrationListener != null)
                nsdManager.unregisterService(registrationListener)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error stopping service advertisement: ${e.message}")
        }
        registrationListener = null
    }

    private fun createRegistrationListener() : NsdManager.RegistrationListener {
        return object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Service registration failed: Error code: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: Error code: $errorCode")
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service registered successfully: $serviceInfo")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Service unregistered successfully: $serviceInfo")
            }
        }
    }

    private fun createDiscoveryListener() : NsdManager.DiscoveryListener{
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Ignore own service
                if (service.serviceName == currentServiceID) return

                Log.d(TAG, "Service found: $service")
                if (service.serviceType == SERVICE_TYPE) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
                            // Unregister previous ServiceInfoCallback if registered
                            try {
                                nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
                                Log.d(TAG, "Successfully unregistered previous ServiceInfoCallback")
                            } catch (e: Exception) {
                                Log.e(TAG, "No ServiceInfoCallback was registered: ${e.message}")
                            }

                            // Add a small delay to avoid race conditions
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    nsdManager.registerServiceInfoCallback(service, executor, serviceInfoCallback)
                                    Log.d(TAG, "Successfully registered ServiceInfoCallback")
                                } catch (e: IllegalArgumentException) {
                                    Log.e(TAG, "Listener already in use or issue in registration: ${e.message}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error registering listener: ${e.message}")
                                }
                            }, 500)
                        } else {
                            val resolveListener = createResolveListener()
                            nsdManager.resolveService(service, resolveListener)
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Listener already in use or issue in registration: ${e.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registering listener: ${e.message}")
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "Service lost: $service")
                _services.value = _services.value.filter { it.serviceName != service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to start: Error code: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to stop: Error code: $errorCode")
            }
        }
    }

    /**
     * Returns a new listener instance since NsdManager wants a different listener each time you call resolveService
     */
    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "MDNS Resolve failed: Error code: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "MDNS Resolved successfully $serviceInfo")
                _services.value = (_services.value + serviceInfo).distinctBy { it.serviceName }
            }
        }
    }

    // ServiceInfoCallback for API level 34+
    private val serviceInfoCallback by lazy {
        @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
        object : NsdManager.ServiceInfoCallback {
            private var currentMonitoredService: NsdServiceInfo? = null

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "ServiceInfoCallback registration failed: $errorCode")
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service updated: $serviceInfo")
                currentMonitoredService = serviceInfo  // Store the current service
                try {
                    _services.value = (_services.value + serviceInfo).distinctBy { it.serviceName }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse service info: ${e.message}")
                }
            }

            override fun onServiceLost() {
                Log.e(TAG, "Service lost")
                currentMonitoredService?.let { lostService ->
                    _services.value = _services.value.filter { it.serviceName != lostService.serviceName }
                }
            }

            override fun onServiceInfoCallbackUnregistered() {
                Log.d(TAG, "ServiceInfoCallback unregistered")
                currentMonitoredService = null
            }
        }
    }

    companion object {
        private const val TAG = "NsdService"
        private const val SERVICE_TYPE = "_sefirah._udp."
    }
}

