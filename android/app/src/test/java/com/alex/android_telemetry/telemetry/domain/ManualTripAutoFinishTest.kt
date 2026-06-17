package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualTripAutoFinishTest {

    @Test
    fun auto_finish_triggers_after_150s_non_automotive_and_low_speed() {
        val runtime = FakeManualTripRuntime()

        runtime.startManualTrip()

        runtime.updateSpeed(3.0)
        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 0
        )

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 149
        )

        assertFalse(runtime.stopTriggered)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 150
        )

        assertTrue(runtime.stopTriggered)
    }

    @Test
    fun speed_above_7_resets_timer() {
        val runtime = FakeManualTripRuntime()

        runtime.startManualTrip()

        runtime.updateSpeed(3.0)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 0
        )

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 100
        )

        runtime.updateSpeed(8.0)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 101
        )

        runtime.updateSpeed(3.0)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 249
        )

        assertFalse(runtime.stopTriggered)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 399
        )

        assertTrue(runtime.stopTriggered)
    }

    @Test
    fun automotive_activity_resets_timer() {
        val runtime = FakeManualTripRuntime()

        runtime.startManualTrip()

        runtime.updateSpeed(3.0)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 0
        )

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 100
        )

        runtime.recordActivity(
            dominant = "automotive",
            timestampSec = 101
        )

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 240
        )

        assertFalse(runtime.stopTriggered)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 390
        )

        assertTrue(runtime.stopTriggered)

    }

    @Test
    fun day_monitoring_trip_is_not_auto_finished() {
        val runtime = FakeManualTripRuntime()

        runtime.startDayMonitoringTrip()

        runtime.updateSpeed(2.0)

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 0
        )

        runtime.recordActivity(
            dominant = "walking",
            timestampSec = 500
        )

        assertFalse(runtime.stopTriggered)
    }
}

private class FakeManualTripRuntime {

    private var collecting = false
    private var trackingMode = "none"

    private var speedKmh: Double = 0.0

    private var manualNonAutomotiveSinceSec: Long? = null

    var stopTriggered = false
        private set

    fun startManualTrip() {
        collecting = true
        trackingMode = "single_trip"
    }

    fun startDayMonitoringTrip() {
        collecting = true
        trackingMode = "day_monitoring"
    }

    fun updateSpeed(speedKmh: Double) {
        this.speedKmh = speedKmh
    }

    fun recordActivity(
        dominant: String,
        timestampSec: Long,
    ) {
        maybeAutoFinishManualTrip(
            dominant = dominant,
            timestampSec = timestampSec,
        )
    }

    private fun maybeAutoFinishManualTrip(
        dominant: String,
        timestampSec: Long,
    ) {
        if (!collecting) return
        if (trackingMode != "single_trip") return
        if (stopTriggered) return

        if (speedKmh > 7.0) {
            manualNonAutomotiveSinceSec = null
            return
        }

        if (dominant == "automotive") {
            manualNonAutomotiveSinceSec = null
            return
        }

        val startedAt = manualNonAutomotiveSinceSec

        if (startedAt == null) {
            manualNonAutomotiveSinceSec = timestampSec
            return
        }

        val elapsed = timestampSec - startedAt

        if (elapsed >= 150 && speedKmh < 5.0) {
            stopTriggered = true
            manualNonAutomotiveSinceSec = null
        }
    }
}