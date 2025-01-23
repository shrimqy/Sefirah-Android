package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocalDevice(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val privateKey: String,
    val wallpaperBase64: String? = null
) : Parcelable