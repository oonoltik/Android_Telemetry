package com.alex.android_telemetry.telemetry.capture.location

data class LocationCaptureConfig(
    val intervalMillis: Long = 2_000L,
    val minUpdateIntervalMillis: Long = 1_000L,
    val maxUpdateDelayMillis: Long = 5_000L,
    val minDistanceMeters: Float = 0f
)
