package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConnectionDetails(
    val deviceId: String,
    val prefAddress: String?,
    val addresses: List<String>,
    val port: Int,
    val publicKey: String
) : Parcelable

