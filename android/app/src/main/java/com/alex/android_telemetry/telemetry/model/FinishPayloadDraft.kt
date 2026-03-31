package com.alex.android_telemetry.telemetry.model

import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode

data class FinishPayloadDraft(
    val deviceId: String,
    val driverId: String?,
    val sessionId: String,
    val clientEndedAt: String,
    val trackingMode: TrackingMode,
    val transportMode: TransportMode,
    val tripDurationSec: Double?,
    val finishReason: FinishReason
)
