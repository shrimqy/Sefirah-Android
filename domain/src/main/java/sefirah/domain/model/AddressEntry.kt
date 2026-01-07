package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class AddressEntry(
    val address: String,
    val isEnabled: Boolean = false,
    val priority: Int = 0
) : Parcelable

