package com.alex.android_telemetry.telemetry.model

import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode

data class TelemetryBatchDraft(
    val deviceId: String,
    val driverId: String?,
    val sessionId: String,
    val timestamp: String,
    val batchId: String,
    val batchSeq: Int,
    val trackingMode: TrackingMode,
    val transportMode: TransportMode,
    val samples: List<TelemetrySampleDraft>
)
