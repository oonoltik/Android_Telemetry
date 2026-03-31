package com.alex.android_telemetry.telemetry.delivery.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxDao
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxEntity
import com.alex.android_telemetry.telemetry.storage.runtime.ActiveTripDao
import com.alex.android_telemetry.telemetry.storage.runtime.ActiveTripEntity

@Database(
    entities = [
        TelemetryOutboxEntity::class,
        ActiveTripEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun telemetryOutboxDao(): TelemetryOutboxDao
    abstract fun activeTripDao(): ActiveTripDao

    companion object {
        @Volatile
        private var INSTANCE: TelemetryDatabase? = null

        fun get(context: Context): TelemetryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TelemetryDatabase::class.java,
                    "telemetry.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}