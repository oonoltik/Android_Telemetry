package com.alex.android_telemetry.telemetry.batching

data class BatchFlushPolicy(
    val maxWindowMs: Long = 15_000,
    val maxFrames: Int = 50,
)