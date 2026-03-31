package com.alex.android_telemetry.telemetry.storage.runtime

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_trip")
data class ActiveTripEntity(
    @PrimaryKey val sessionId: String,
    val deviceId: String,
    val driverId: String?,
    val trackingMode: String,
    val transportMode: String,
    val startedAt: String,
    val startedAtEpochMillis: Long,
    val nextBatchSeq: Int,
    val isActive: Boolean
)
