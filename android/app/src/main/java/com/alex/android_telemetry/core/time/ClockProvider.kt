package com.alex.android_telemetry.core.time

interface ClockProvider {
    fun nowEpochMillis(): Long
    fun nowIsoStringUtc(): String
    fun elapsedRealtimeMillis(): Long
}
