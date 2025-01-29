package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.security.cert.X509Certificate

@Parcelize
data class RemoteDevice(
    val deviceId: String,
    val ipAddresses: List<String>,  
    val prefAddress: String? = null,
    val port: Int,
    val publicKey: String,
    val deviceName: String,
    val avatar: String? = null,
    var lastConnected: Long? = null,
) : Parcelable
