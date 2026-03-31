package com.alex.android_telemetry.telemetry.model

data class RuntimeCounters(
    val samplesBuffered: Int = 0,
    val batchesCreated: Int = 0,
    val batchesDelivered: Int = 0,
    val elapsedSec: Long = 0L
)
