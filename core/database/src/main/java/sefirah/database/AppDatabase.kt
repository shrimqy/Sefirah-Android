package sefirah.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import sefirah.database.dao.DeviceDao
import sefirah.database.dao.NetworkDao
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.NetworkEntity
import sefirah.database.model.RemoteDeviceEntity

interface AppDatabase {
    fun devicesDao(): DeviceDao
    fun networkDao(): NetworkDao

    /**
     * Execute the whole database calls as an atomic operation
     */
    suspend fun <T> transaction(block: suspend () -> T): T

    companion object {
        private const val DATABASE_NAME = "sefirah.db"
        
        fun createRoom(context: Context): AppDatabase = Room
            .databaseBuilder(context, AppRoomDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }
}

@Database(
    entities = [
        RemoteDeviceEntity::class,
        NetworkEntity::class,
        LocalDeviceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
internal abstract class AppRoomDatabase : RoomDatabase(), AppDatabase {
    abstract override fun devicesDao(): DeviceDao
    abstract override fun networkDao(): NetworkDao

    override suspend fun <T> transaction(block: suspend () -> T): T = withTransaction(block)
}