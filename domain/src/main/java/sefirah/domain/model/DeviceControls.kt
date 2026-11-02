package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
@Parcelize
data class DeviceControls(
    val version: Int = 1,
    val ringerControl: Boolean = false,
    val bluetoothControl: String = "unsupported",
    val bluetoothEnabled: Boolean? = null,
) : Parcelable

@Serializable
@SerialName("DeviceControlRequest")
data class DeviceControlRequest(val requestId: String, val control: String, val value: Int) : SocketMessage()

@Serializable
@SerialName("DeviceControlResult")
data class DeviceControlResult(val requestId: String, val status: String) : SocketMessage()
