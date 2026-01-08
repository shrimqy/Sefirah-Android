package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.PairedDevice

@Entity
data class RemoteDeviceEntity (
    @PrimaryKey val deviceId: String,
    val addresses: List<AddressEntry>,
    val publicKey: String,
    val deviceName: String,
    val avatar: String? = null,
    var lastConnected: Long? = null,
)

fun RemoteDeviceEntity.toDomain(): PairedDevice {
    return PairedDevice(
        deviceId = deviceId,
        addresses = addresses,
        avatar = avatar,
        publicKey = publicKey,
        deviceName = deviceName,
        lastConnected = lastConnected,
        port = null // Port is discovered at runtime, not persisted
    )
}

fun List<RemoteDeviceEntity>.toDomain(): List<PairedDevice> {
    return map { it.toDomain() }
}

fun PairedDevice.toEntity(): RemoteDeviceEntity {
    return RemoteDeviceEntity(
        deviceId = deviceId,
        deviceName = deviceName,
        addresses = addresses,
        avatar = avatar,
        publicKey = publicKey,
        lastConnected = lastConnected
    )
}
