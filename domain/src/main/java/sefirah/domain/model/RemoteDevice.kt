package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.security.cert.X509Certificate

@Parcelize
data class RemoteDevice(
    val deviceId: String,
    val ipAddress: String,
    val port: Int,
    val publicKey: String,
    val deviceName: String,
    val certificate: X509Certificate,
    val avatar: String? = null,
    var hashedSecret: String? = null,
    var lastConnected: Long? = null,
) : Parcelable
