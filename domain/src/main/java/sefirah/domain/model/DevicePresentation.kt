package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class DevicePresentation(
    val version: Int = 1,
    val kind: String = "unknown",
    val orientation: String = "landscape",
    val aspectRatio: String = "16:9",
    val wallpaperSource: String = "none",
    val wallpaperId: String? = null,
    val wallpaper: String? = null,
) : Parcelable {
    fun isValid() = version == 1 && kind in kinds && orientation in orientations &&
        aspectRatio in ratios && wallpaperSource in listOf("none", "wallpaper", "userSelected") &&
        (wallpaperSource == "none" || wallpaperId?.matches(Regex("[a-fA-F0-9]{64}")) == true)
    fun metadata() = copy(wallpaper = null)
    fun displayRatio(): Float {
        if (!isValid()) return 1f
        val parts = aspectRatio.split(":")
        val r = parts[0].toFloat() / parts[1].toFloat()
        val landscape = maxOf(r, 1 / r)
        return if (orientation == "landscape") landscape else 1 / landscape
    }
    companion object {
        val kinds = listOf("unknown", "phone", "tablet", "computer")
        val orientations = listOf("portrait", "landscape")
        val ratios = listOf("9:16", "9:19.5", "9:20", "4:3", "3:2", "16:9", "16:10")
    }
}
