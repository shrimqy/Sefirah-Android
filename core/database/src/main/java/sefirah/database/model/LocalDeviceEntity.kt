package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.domain.model.LocalDevice

@Entity
data class LocalDeviceEntity (
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val model: String,
    val publicKey: String,
    val privateKey: String,
)

fun LocalDeviceEntity.toDomain(): LocalDevice = LocalDevice(
    deviceId = deviceId,
    deviceName = deviceName,
    model = model,
    publicKey = publicKey,
    privateKey = privateKey,
)

fun LocalDevice.toEntity(): LocalDeviceEntity = LocalDeviceEntity(
    deviceId = deviceId,
    deviceName = deviceName,
    model = model,
    publicKey = publicKey,
    privateKey = privateKey,
)
