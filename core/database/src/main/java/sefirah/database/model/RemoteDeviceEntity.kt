package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.domain.model.RemoteDevice

@Entity
data class RemoteDeviceEntity (
    @PrimaryKey val deviceId: String,
    val ipAddress: String,
    val port: Int,
    val publicKey: String,
    val deviceName: String,
    val avatar: String? = null,
    var hashedSecret: String? = null,
    var lastConnected: Long? = null,
)

fun RemoteDeviceEntity.toDomain(): RemoteDevice {
    return RemoteDevice(
        deviceId = deviceId,
        ipAddress = ipAddress,
        port = port,
        avatar = avatar,
        publicKey = publicKey,
        deviceName = deviceName,
        hashedSecret = hashedSecret,
        lastConnected = lastConnected
    )
}

fun List<RemoteDeviceEntity>.toDomain(): List<RemoteDevice> {
    return map { it.toDomain() }
}

fun RemoteDevice.toEntity(): RemoteDeviceEntity {
    return RemoteDeviceEntity(
        deviceId = deviceId,
        ipAddress = ipAddress,
        port = port,
        avatar = avatar,
        publicKey = publicKey,
        deviceName = deviceName,
        hashedSecret = hashedSecret,
        lastConnected = lastConnected
    )
}
