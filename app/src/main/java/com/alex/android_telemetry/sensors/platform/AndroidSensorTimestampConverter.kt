package com.alex.android_telemetry.sensors.platform

import kotlinx.datetime.Instant

/**
 * Converts Android sensor event timestamps (nanoseconds since boot) into wall-clock Instants.
 */
class AndroidSensorTimestampConverter(
    private val nowWallClockMsProvider: () -> Long = { System.currentTimeMillis() },
    private val nowElapsedRealtimeNsProvider: () -> Long,
) {
    fun toInstant(eventTimestampNs: Long): Instant {
        val nowWallClockMs = nowWallClockMsProvider()
        val nowElapsedNs = nowElapsedRealtimeNsProvider()
        val deltaNs = nowElapsedNs - eventTimestampNs
        val eventWallClockMs = nowWallClockMs - (deltaNs / 1_000_000L)
        return Instant.fromEpochMilliseconds(eventWallClockMs)
    }
}
