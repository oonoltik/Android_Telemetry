package com.alex.android_telemetry.telemetry.session

import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode

data class TripSession(
    val sessionId: String,
    val deviceId: String,
    val driverId: String?,
    val trackingMode: TrackingMode,
    val transportMode: TransportMode,
    val startedAt: String,
    val startedAtEpochMillis: Long,
    val nextBatchSeq: Int = 1,
    val isActive: Boolean = true
)
