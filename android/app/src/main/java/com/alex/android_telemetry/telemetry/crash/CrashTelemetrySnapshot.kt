package com.alex.android_telemetry.telemetry.crash

import kotlinx.serialization.Serializable

@Serializable
data class CrashTelemetrySnapshot(
    val capturedAtMs: Long,
    val capturedAtIso: String,
    val tripSessionId: String?,
    val lat: Double?,
    val lon: Double?,
    val speedKmh: Double?,
    val headingDeg: Double?,
    val horizontalAccuracyM: Double?,
)