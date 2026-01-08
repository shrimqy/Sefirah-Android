package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class QrCodeConnectionData(
    val deviceId: String,
    val deviceName: String,
    val addresses: List<String>,
    val port: Int,
    val publicKey: String
) : Parcelable