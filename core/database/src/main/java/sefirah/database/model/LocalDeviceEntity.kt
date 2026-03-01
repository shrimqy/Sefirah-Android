package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.domain.model.LocalDevice

@Entity
data class LocalDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val model: String,
)

fun LocalDeviceEntity.toDomain(): LocalDevice = LocalDevice(
    deviceId = deviceId,
    deviceName = deviceName,
    model = model,
)

fun LocalDevice.toEntity(): LocalDeviceEntity = LocalDeviceEntity(
    deviceId = deviceId,
    deviceName = deviceName,
    model = model,
)
