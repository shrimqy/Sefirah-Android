package sefirah.database.model

import androidx.room.Entity

@Entity(primaryKeys = ["deviceId", "ssid"])
data class DeviceNetworkCrossRef(
    val deviceId: String,
    val ssid: String
) 