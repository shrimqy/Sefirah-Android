package sefirah.network.util

import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

fun getDeviceIpAddress(): String? {
    try {
        var fallbackIp: String? = null
        
        Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { networkInterface ->
            val isWifi = networkInterface.name.startsWith("wlan")
            
            Collections.list(networkInterface.inetAddresses).forEach { address ->
                if (!address.isLoopbackAddress) {
                    val hostAddress = address.hostAddress
                    if (hostAddress != null && !hostAddress.contains(":")) { // IPv4 only
                        if (isWifi) {
                            return hostAddress // return WiFi address if found
                        } else if (fallbackIp == null) {
                            fallbackIp = hostAddress 
                        }
                    }
                }
            }
        }
        // Return fallback
        if (fallbackIp != null) {
            Log.d("DeviceIp", "Using fallback IP: $fallbackIp")
            return fallbackIp
        }
        
        Log.d("DeviceIp", "No suitable IP address found")
        return null
    } catch (e: Exception) {
        Log.e("DeviceIp", "Error getting device IP address", e)
        return null
    }
}