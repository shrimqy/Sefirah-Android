package sefirah.database.model

import android.util.Base64
import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.common.util.getCertFromString
import sefirah.domain.model.RemoteDevice

@Entity
data class RemoteDeviceEntity (
    @PrimaryKey val deviceId: String,
    val ipAddresses: List<String> ,
    val prefAddress: String? = null,
    val port: Int,
    val publicKey: String,
    val deviceName: String,
    val avatar: String? = null,
    var lastConnected: Long? = null,
    val certificate: String
)

fun RemoteDeviceEntity.toDomain(): RemoteDevice {
    return RemoteDevice(
        deviceId = deviceId,
        ipAddresses = ipAddresses,
        prefAddress = prefAddress,
        port = port,
        avatar = avatar,
        publicKey = publicKey,
        deviceName = deviceName,
        lastConnected = lastConnected,
        certificate = getCertFromString(certificate)
    )
}

fun List<RemoteDeviceEntity>.toDomain(): List<RemoteDevice> {
    return map { it.toDomain() }
}

fun RemoteDevice.toEntity(): RemoteDeviceEntity {
    return RemoteDeviceEntity(
        deviceId = deviceId,
        ipAddresses = ipAddresses,
        prefAddress = prefAddress,
        port = port,
        avatar = avatar,
        publicKey = publicKey,
        deviceName = deviceName,
        lastConnected = lastConnected,
        certificate = Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
    )
}
