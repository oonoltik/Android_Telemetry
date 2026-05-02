package com.alex.android_telemetry.telemetry.domain.model

data class TelemetryCounters(
    val samplesBuffered: Int = 0,
    val batchesCreated: Int = 0,
    val batchesDelivered: Int = 0,
)