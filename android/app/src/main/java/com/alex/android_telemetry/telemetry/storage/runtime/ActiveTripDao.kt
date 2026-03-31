package com.alex.android_telemetry.telemetry.storage.runtime

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActiveTripDao {
    @Query("SELECT * FROM active_trip LIMIT 1")
    suspend fun getActiveTrip(): ActiveTripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActiveTripEntity)

    @Query("DELETE FROM active_trip")
    suspend fun clear()
}
