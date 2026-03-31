package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode
import com.alex.android_telemetry.telemetry.domain.TripStatus

data class TripRuntimeSnapshot(
    val status: TripStatus = TripStatus.Idle,
    val isTracking: Boolean = false,
    val sessionId: String? = null,
    val driverId: String? = null,
    val trackingMode: TrackingMode? = null,
    val transportMode: TransportMode? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val elapsedSec: Long = 0L,
    val samplesBuffered: Int = 0,
    val batchesCreated: Int = 0,
    val batchesDelivered: Int = 0,
    val euDelivered: Int = 0,
    val ruDelivered: Int = 0,
    val finishPending: Boolean = false,
    val lastError: String? = null,
    val lastReportSessionId: String? = null
)
