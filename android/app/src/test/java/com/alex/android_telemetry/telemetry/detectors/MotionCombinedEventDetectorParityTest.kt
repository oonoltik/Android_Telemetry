package com.alex.android_telemetry.telemetry.detectors

import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class MotionCombinedEventDetectorParityTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = true
    }

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
        roadCooldownS = 1.2,
        roadWindowS = 0.4,
        combinedLatMinG = 0.35,
        accelInTurnSharpG = 0.18,
        accelInTurnEmergencyG = 0.28,
        brakeInTurnSharpG = 0.22,
        brakeInTurnEmergencyG = 0.32,
        combinedCooldownS = 0.8,
    )

    @Test
    fun combined_events_match_expected_output() {
        val fixtures = loadFixtures()
        assert(fixtures.isNotEmpty())

        fixtures.forEach { fixture ->
            val detector = makeDetector(fixture.detector)

            val actual = detector.detect(
                vector = MotionVector(
                    aLongG = fixture.input.aLongG,
                    aLatG = fixture.input.aLatG,
                    aVertG = fixture.input.aVertG,
                    yawRate = null,
                    speedMS = fixture.input.speedMS,
                ),
                now = Instant.parse(fixture.input.t),
            )

            assertEventEquals(fixture.name, fixture.expected, actual)
        }
    }

    private fun makeDetector(name: String): TelemetryEventDetector {
        return when (name) {
            "accel_in_turn" -> AccelInTurnDetector { thresholds }
            "brake_in_turn" -> BrakeInTurnDetector { thresholds }
            else -> error("Unsupported detector '$name'")
        }
    }

    private fun assertEventEquals(
        name: String,
        expected: ExpectedCombinedEvent?,
        actual: DetectedTelemetryEvent?,
    ) {
        if (expected == null) {
            assertNull("$name expected no event", actual)
            return
        }

        assertNotNull("$name expected event", actual)
        actual ?: return

        assertEquals("$name.type", TelemetryEventType.valueOf(expected.type), actual.type)
        assertAlmostEquals("$name.intensity", expected.intensity, actual.intensity)
        assertAlmostEquals("$name.speedMS", expected.speedMS, actual.speedMS)
        assertEquals("$name.eventClass", expected.eventClass, actual.eventClass)
    }

    private fun assertAlmostEquals(
        label: String,
        expected: Double?,
        actual: Double?,
        tolerance: Double = 1e-6,
    ) {
        if (expected == null || actual == null) {
            assertEquals(label, expected, actual)
            return
        }
        assertEquals(label, expected, actual, tolerance)
    }

    private fun loadFixtures(): List<CombinedEventFixture> {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("motion_combined_events_expected.json")

        assertNotNull("Missing test resource motion_combined_events_expected.json", stream)

        return stream!!.use {
            json.decodeFromString<List<CombinedEventFixture>>(
                it.bufferedReader().readText()
            )
        }
    }
}

@Serializable
data class CombinedEventFixture(
    val name: String,
    val detector: String,
    val input: CombinedEventInput,
    val expected: ExpectedCombinedEvent? = null,
)

@Serializable
data class CombinedEventInput(
    val t: String,
    @kotlinx.serialization.SerialName("speed_m_s")
    val speedMS: Double,
    @kotlinx.serialization.SerialName("a_long_g")
    val aLongG: Double,
    @kotlinx.serialization.SerialName("a_lat_g")
    val aLatG: Double,
    @kotlinx.serialization.SerialName("a_vert_g")
    val aVertG: Double,
)

@Serializable
data class ExpectedCombinedEvent(
    val type: String,
    val intensity: Double,
    @kotlinx.serialization.SerialName("speed_m_s")
    val speedMS: Double,
    @kotlinx.serialization.SerialName("event_class")
    val eventClass: String,
)