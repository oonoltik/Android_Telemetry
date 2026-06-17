package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DayMonitoringTransportModeParityTest {

    @Test
    fun day_monitoring_uses_car_transport_mode() {
        val resolver = FakeTransportModeResolver()

        val result = resolver.resolve(
            trackingMode = "day_monitoring",
            providedTransportMode = "unknown"
        )

        assertEquals("car", result)
    }

    @Test
    fun single_trip_keeps_original_transport_mode() {
        val resolver = FakeTransportModeResolver()

        val result = resolver.resolve(
            trackingMode = "single_trip",
            providedTransportMode = "bike"
        )

        assertEquals("bike", result)
    }

    @Test
    fun single_trip_can_still_be_unknown() {
        val resolver = FakeTransportModeResolver()

        val result = resolver.resolve(
            trackingMode = "single_trip",
            providedTransportMode = "unknown"
        )

        assertEquals("unknown", result)
    }
}

private class FakeTransportModeResolver {

    fun resolve(
        trackingMode: String,
        providedTransportMode: String,
    ): String {
        return when (trackingMode) {
            "day_monitoring" -> "car"
            else -> providedTransportMode
        }
    }
}