package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import sefirah.domain.model.LocalDevice

@Entity
data class LocalDeviceEntity (
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val privateKey: String,
    val wallpaperBase64: String? = null
)

fun LocalDeviceEntity.toDomain(): LocalDevice = LocalDevice(
    deviceId = deviceId,
    deviceName = deviceName,
    publicKey = publicKey,
    privateKey = privateKey,
    wallpaperBase64 = wallpaperBase64
)

fun LocalDevice.toEntity(): LocalDeviceEntity = LocalDeviceEntity(
    deviceId = deviceId,
    deviceName = deviceName,
    publicKey = publicKey,
    privateKey = privateKey,
    wallpaperBase64 = wallpaperBase64
)
