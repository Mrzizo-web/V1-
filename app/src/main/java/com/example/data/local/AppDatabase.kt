package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.EventLogDao
import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.dao.PaymentAttemptDao
import com.example.data.local.dao.PaymentDao
import com.example.data.local.dao.SmsMessageDao
import com.example.data.local.entity.EventLogEntity
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.PaymentAttemptEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.SmsMessageEntity

@Database(
    entities = [
        SmsMessageEntity::class,
        PaymentEntity::class,
        PaymentAttemptEntity::class,
        GatewayConfigEntity::class,
        EventLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun paymentDao(): PaymentDao
    abstract fun paymentAttemptDao(): PaymentAttemptDao
    abstract fun gatewayConfigDao(): GatewayConfigDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Rebuild instead of DROP COLUMN: older Android SQLite versions do not support it.
                // Copy retained configuration values and discard only the retired wallet field.
                database.execSQL("""
                    CREATE TABLE gateway_config_new (
                        id INTEGER NOT NULL,
                        deviceId TEXT NOT NULL,
                        posIpAddress TEXT NOT NULL,
                        posPort INTEGER NOT NULL,
                        connectionMode TEXT NOT NULL,
                        isAutoSyncEnabled INTEGER NOT NULL,
                        maxRetryCount INTEGER NOT NULL,
                        retryDelaySeconds INTEGER NOT NULL,
                        isLoggingEnabled INTEGER NOT NULL,
                        isGatewayActive INTEGER NOT NULL,
                        adminPin TEXT NOT NULL,
                        jeebSenderKeyword TEXT NOT NULL,
                        floosakSenderKeyword TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO gateway_config_new (
                        id, deviceId, posIpAddress, posPort, connectionMode,
                        isAutoSyncEnabled, maxRetryCount, retryDelaySeconds,
                        isLoggingEnabled, isGatewayActive, adminPin,
                        jeebSenderKeyword, floosakSenderKeyword
                    )
                    SELECT
                        id, deviceId, posIpAddress, posPort, connectionMode,
                        isAutoSyncEnabled, maxRetryCount, retryDelaySeconds,
                        isLoggingEnabled, isGatewayActive, adminPin,
                        jeebSenderKeyword, floosakSenderKeyword
                    FROM gateway_config
                """.trimIndent())
                database.execSQL("DROP TABLE gateway_config")
                database.execSQL("ALTER TABLE gateway_config_new RENAME TO gateway_config")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "power_fuel_sms_gateway.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
