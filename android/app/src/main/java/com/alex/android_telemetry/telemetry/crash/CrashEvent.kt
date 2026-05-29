package com.alex.android_telemetry.telemetry.crash

data class CrashEvent(
    val detectedAtMs: Long,
    val gForce: Double,
    val source: String = "accelerometer",
)