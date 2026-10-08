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
                database.execSQL("ALTER TABLE gateway_config DROP COLUMN hawalySenderKeyword")
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
