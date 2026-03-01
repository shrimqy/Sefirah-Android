package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.PairedDevice

@Entity
data class PairedDeviceEntity (
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val addresses: List<AddressEntry>,
    val certificate: ByteArray,
    val avatar: String? = null,
    var lastConnected: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PairedDeviceEntity

        if (lastConnected != other.lastConnected) return false
        if (deviceId != other.deviceId) return false
        if (addresses != other.addresses) return false
        if (deviceName != other.deviceName) return false
        if (avatar != other.avatar) return false
        if (!certificate.contentEquals(other.certificate)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lastConnected?.hashCode() ?: 0
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + addresses.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + (avatar?.hashCode() ?: 0)
        result = 31 * result + certificate.contentHashCode()
        return result
    }
}

fun PairedDeviceEntity.toDomain(): PairedDevice {
    return PairedDevice(deviceId, deviceName, certificate, addresses, avatar, lastConnected)
}

fun List<PairedDeviceEntity>.toDomain(): List<PairedDevice> {
    return map { it.toDomain() }
}

fun PairedDevice.toEntity(): PairedDeviceEntity {
    return PairedDeviceEntity(deviceId, deviceName, addresses, certificate, avatar, lastConnected)
}
