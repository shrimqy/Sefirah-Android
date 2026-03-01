package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConnectionDetails(
    val deviceId: String,
    val port: Int,
    val addresses: List<String>,
    val prefAddress: String? = null,
) : Parcelable

