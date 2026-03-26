package com.alex.android_telemetry.telemetry.delivery

data class TelemetryDeliveryPolicy(
    val maxBatchCountPerRun: Int = 20,
    val maxAttempts: Int = 8,
    val baseBackoffMs: Long = 2_000,
    val maxBackoffMs: Long = 15 * 60 * 1000,
    val inflightTimeoutMs: Long = 2 * 60 * 1000,
)