package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class NetworkEntity (
    @PrimaryKey val ssid: String,
    val isEnabled: Boolean
)