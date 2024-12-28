package sefirah.network.util

import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

fun getDeviceIpAddress(): String? {
    val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
    for (networkInterface in interfaces) {
        val addresses = Collections.list(networkInterface.inetAddresses)
        for (address in addresses) {
            if (!address.isLoopbackAddress && address is InetAddress) {
                val hostAddress = address.hostAddress
                if (hostAddress != null) {
                    if (!hostAddress.contains(":")) { // Skip IPv6 addresses
                        return hostAddress
                    }
                }
            }
        }
    }
    return null
}