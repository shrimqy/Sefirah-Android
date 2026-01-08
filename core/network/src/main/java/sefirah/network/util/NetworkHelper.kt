package sefirah.network.util

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

object NetworkHelper {
    val localAddress: String?
        get() = getDeviceIpAddress()?.hostAddress

    // rmnet is related to cellular connections or USB tethering mechanisms.
    // See: https://android.googlesource.com/kernel/msm/+/android-msm-flo-3.4-kitkat-mr1/Documentation/usb/gadget_rmnet.txt
    fun getDeviceIpAddress(): InetAddress? {
        var ip6: InetAddress? = null
        try {
            for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
                if (networkInterface.displayName.contains("rmnet")) {
                    continue
                }

                for (inetAddress in networkInterface.inetAddresses) {
                    if (inetAddress.isLoopbackAddress) continue
                    ip6 = if (inetAddress is Inet4Address) return inetAddress
                    else inetAddress
                }
            }
        } catch (_: SocketException) {
        }

        return ip6
    }
}
