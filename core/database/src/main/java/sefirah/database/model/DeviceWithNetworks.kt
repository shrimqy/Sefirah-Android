package sefirah.database.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class DeviceWithNetworks(
    @Embedded val device: RemoteDeviceEntity,
    @Relation(
        parentColumn = "deviceId",
        entityColumn = "ssid",
        associateBy = Junction(DeviceNetworkCrossRef::class)
    )
    val networks: List<NetworkEntity>
) 