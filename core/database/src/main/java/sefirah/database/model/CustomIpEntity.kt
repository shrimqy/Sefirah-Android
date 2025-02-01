package sefirah.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class CustomIpEntity(
    @PrimaryKey val ipAddress: String,
)