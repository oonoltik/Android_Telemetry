package com.alex.android_telemetry.telemetry.batching

data class BatchFlushPolicy(
    val maxSamplesPerBatch: Int = 30,
    val flushIntervalMillis: Long = 15_000L
) {
    fun shouldFlush(sampleCount: Int, elapsedMillisSinceLastFlush: Long): Boolean {
        return sampleCount >= maxSamplesPerBatch || elapsedMillisSinceLastFlush >= flushIntervalMillis
    }
}
