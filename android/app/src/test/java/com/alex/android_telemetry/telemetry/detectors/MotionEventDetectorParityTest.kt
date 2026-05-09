package com.alex.android_telemetry.telemetry.detectors

import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class MotionEventDetectorParityTest {

    private val thresholds = EventThresholdSet(
        accelSharpG = 0.18,
        accelEmergencyG = 0.28,
        brakeSharpG = 0.22,
        brakeEmergencyG = 0.32,
        turnSharpG = 0.22,
        turnEmergencyG = 0.30,
        roadLowG = 0.70,
        roadHighG = 1.10,
        minSpeedForAccelBrakeMS = 3.0,
        minSpeedForTurnMS = 5.0,
        accelBrakeCooldownS = 1.2,
        turnCooldownS = 0.8,
        roadCooldownS = 1.0,
        roadWindowS = 0.4,
        combinedLatMinG = 0.35,
        accelInTurnSharpG = 0.18,
        accelInTurnEmergencyG = 0.28,
        brakeInTurnSharpG = 0.22,
        brakeInTurnEmergencyG = 0.32,
        combinedCooldownS = 0.8,
    )

    @Test
    fun accel_in_turn_detects_sharp_case() {
        val detector = AccelInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = 0.24,
                aLatG = 0.38,
                aVertG = 0.02,
                speedMS = 12.0,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNotNull(event)
        assertEquals(TelemetryEventType.ACCEL_IN_TURN, event!!.type)
        assertEquals("sharp", event.eventClass)
        assertEquals(0.24, event.intensity, EPS)
        assertEquals(12.0, event.speedMS ?: 0.0, EPS)
        assertEquals("v2", event.algoVersion)
    }

    @Test
    fun accel_in_turn_detects_emergency_case() {
        val detector = AccelInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = 0.31,
                aLatG = -0.42,
                aVertG = 0.01,
                speedMS = 14.0,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNotNull(event)
        assertEquals(TelemetryEventType.ACCEL_IN_TURN, event!!.type)
        assertEquals("emergency", event.eventClass)
        assertEquals(0.31, event.intensity, EPS)
        assertEquals(14.0, event.speedMS ?: 0.0, EPS)
        assertEquals("v2", event.algoVersion)
    }

    @Test
    fun accel_in_turn_does_not_fire_without_lateral_load() {
        val detector = AccelInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = 0.31,
                aLatG = 0.20,
                aVertG = 0.01,
                speedMS = 14.0,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNull(event)
    }

    @Test
    fun accel_in_turn_does_not_fire_below_turn_speed_gate() {
        val detector = AccelInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = 0.31,
                aLatG = 0.42,
                aVertG = 0.01,
                speedMS = 4.9,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNull(event)
    }

    @Test
    fun brake_in_turn_detects_sharp_case() {
        val detector = BrakeInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = -0.26,
                aLatG = 0.39,
                aVertG = 0.02,
                speedMS = 12.0,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNotNull(event)
        assertEquals(TelemetryEventType.BRAKE_IN_TURN, event!!.type)
        assertEquals("sharp", event.eventClass)
        assertEquals(0.26, event.intensity, EPS)
        assertEquals(12.0, event.speedMS ?: 0.0, EPS)
        assertEquals("v2", event.algoVersion)
    }

    @Test
    fun brake_in_turn_detects_emergency_case() {
        val detector = BrakeInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = -0.36,
                aLatG = -0.44,
                aVertG = 0.02,
                speedMS = 16.0,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNotNull(event)
        assertEquals(TelemetryEventType.BRAKE_IN_TURN, event!!.type)
        assertEquals("emergency", event.eventClass)
        assertEquals(0.36, event.intensity, EPS)
        assertEquals(16.0, event.speedMS ?: 0.0, EPS)
        assertEquals("v2", event.algoVersion)
    }

    @Test
    fun brake_in_turn_does_not_fire_without_lateral_load() {
        val detector = BrakeInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = -0.36,
                aLatG = 0.20,
                aVertG = 0.02,
                speedMS = 16.0,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNull(event)
    }

    @Test
    fun brake_in_turn_does_not_fire_below_turn_speed_gate() {
        val detector = BrakeInTurnDetector { thresholds }

        val event = detector.detect(
            vector = MotionVector(
                aLongG = -0.36,
                aLatG = 0.44,
                aVertG = 0.02,
                speedMS = 4.9,
            ),
            now = Instant.parse("2026-04-30T12:00:00.000Z"),
        )

        assertNull(event)
    }

    @Test
    fun combined_detectors_respect_cooldown() {
        val accelDetector = AccelInTurnDetector { thresholds }
        val brakeDetector = BrakeInTurnDetector { thresholds }

        val t0 = Instant.parse("2026-04-30T12:00:00.000Z")
        val t1 = Instant.parse("2026-04-30T12:00:00.500Z")

        val accelVector = MotionVector(
            aLongG = 0.31,
            aLatG = 0.42,
            aVertG = 0.01,
            speedMS = 14.0,
        )

        val brakeVector = MotionVector(
            aLongG = -0.36,
            aLatG = 0.44,
            aVertG = 0.02,
            speedMS = 16.0,
        )

        assertNotNull(accelDetector.detect(accelVector, t0))
        assertNull(accelDetector.detect(accelVector, t1))

        assertNotNull(brakeDetector.detect(brakeVector, t0))
        assertNull(brakeDetector.detect(brakeVector, t1))
    }

    private companion object {
        const val EPS = 1e-9
    }
}