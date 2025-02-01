package sefirah.database.model

import androidx.room.Entity

@Entity(primaryKeys = ["deviceId", "ipAddress"])
data class DeviceCustomIpCrossRef(
    val deviceId: String,
    val ipAddress: String
) 