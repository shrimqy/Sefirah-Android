package sefirah.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import sefirah.database.dao.DeviceDao
import sefirah.database.dao.NetworkDao
import sefirah.database.model.CustomIpEntity
import sefirah.database.model.LocalDeviceEntity
import sefirah.database.model.NetworkEntity
import sefirah.database.model.RemoteDeviceEntity
import sefirah.database.model.DeviceNetworkCrossRef
import sefirah.database.model.DeviceCustomIpCrossRef

interface AppDatabase {
    fun devicesDao(): DeviceDao
    fun networkDao(): NetworkDao

    /**
     * Execute the whole database calls as an atomic operation
     */
    suspend fun <T> transaction(block: suspend () -> T): T

    companion object {
        private const val DATABASE_NAME = "sefirah.db"
        
        // Migration from version 2 to 3 (removing wallpaperBase64 from LocalDeviceEntity)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS LocalDeviceEntity_new (" +
                    "deviceId TEXT NOT NULL PRIMARY KEY, " +
                    "deviceName TEXT NOT NULL, " +
                    "publicKey TEXT NOT NULL, " +
                    "privateKey TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO LocalDeviceEntity_new (deviceId, deviceName, publicKey, privateKey) " +
                    "SELECT deviceId, deviceName, publicKey, privateKey FROM LocalDeviceEntity"
                )
                db.execSQL("DROP TABLE LocalDeviceEntity")
                db.execSQL("ALTER TABLE LocalDeviceEntity_new RENAME TO LocalDeviceEntity")
            }
        }
        
        fun createRoom(context: Context): AppDatabase = Room
            .databaseBuilder(context, AppRoomDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}


@Database(
    entities = [
        RemoteDeviceEntity::class,
        NetworkEntity::class,
        DeviceNetworkCrossRef::class,
        LocalDeviceEntity::class,
        CustomIpEntity::class,
        DeviceCustomIpCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
internal abstract class AppRoomDatabase : RoomDatabase(), AppDatabase {
    abstract override fun devicesDao(): DeviceDao
    abstract override fun networkDao(): NetworkDao

    override suspend fun <T> transaction(block: suspend () -> T): T = withTransaction(block)
}