package sefirah.network

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log

enum class DiscoverySource { NSD, UDP }

data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val port: Int,
    val ipAddresses: List<String>,
    val timestamp: Long,
    val source: DiscoverySource
)

fun createDiscoveredDevice(serviceInfo: NsdServiceInfo): DiscoveredDevice? {
    val attributes = try {
        serviceInfo.attributes ?: return null
    } catch (e: SecurityException) {
        Log.e("DiscoveredDevice", "Security exception reading attributes", e)
        return null
    }

    // Check for required attributes and their values
    val requiredKeys = listOf(
        "deviceName" to attributes["deviceName"],
        "publicKey" to attributes["publicKey"],
        "serverPort" to attributes["serverPort"]
    )
    
    if (requiredKeys.any { it.second == null }) {
        Log.w("DiscoveredDevice", "Missing required attributes in service info")
        return null
    }

    return try {
        val deviceName = String(requireNotNull(attributes["deviceName"]), Charsets.UTF_8)
        val publicKey = String(requireNotNull(attributes["publicKey"]), Charsets.UTF_8)
        val serverPort = String(requireNotNull(attributes["serverPort"]), Charsets.UTF_8).toInt()
        val serviceName = serviceInfo.serviceName ?: run {
            Log.w("DiscoveredDevice", "Missing service name")
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
            DiscoveredDevice(
                deviceId = serviceName,
                deviceName = deviceName,
                publicKey = publicKey,
                port = serverPort,
                ipAddresses = serviceInfo.hostAddresses.map { it.hostAddress ?: "" }
                    .filter { it.isNotBlank() && !it.contains(":") },
                timestamp = System.currentTimeMillis(),
                source = DiscoverySource.NSD
            )
        } else {
            DiscoveredDevice(
                deviceId = serviceName,
                deviceName = deviceName,
                publicKey = publicKey,
                port = serverPort,
                ipAddresses = listOfNotNull(serviceInfo.host?.hostAddress)
                    .filter { !it.contains(":") },
                timestamp = System.currentTimeMillis(),
                source = DiscoverySource.NSD
            )
        }
    } catch (e: Exception) {
        Log.e("DiscoveredDevice", "Error creating DiscoveredDevice from service info", e)
        null
    }
}