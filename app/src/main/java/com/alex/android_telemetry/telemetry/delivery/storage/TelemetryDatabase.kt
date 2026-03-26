package com.alex.android_telemetry.telemetry.delivery.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxDao
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxEntity

@Database(
    entities = [TelemetryOutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun telemetryOutboxDao(): TelemetryOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: TelemetryDatabase? = null

        fun get(context: Context): TelemetryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TelemetryDatabase::class.java,
                    "telemetry.db",
                ).build().also { INSTANCE = it }
            }
    }
}