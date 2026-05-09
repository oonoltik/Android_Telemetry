package com.alex.android_telemetry.telemetry.detectors

import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class RoadAnomalyDetectorParityTest {

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
    fun road_anomaly_window_cases_match_expected_output() {
        val fixtures = loadFixtures()
        assert(fixtures.isNotEmpty())

        fixtures.forEach { fixture ->
            val detector = RoadAnomalyDetector { thresholds }
            var actual: DetectedTelemetryEvent? = null

            fixture.points.forEach { point ->


                val detected = detector.detect(
                    vector = MotionVector(
                        aLongG = null,
                        aLatG = null,
                        aVertG = point.aVertG,
                        yawRate = null,
                        speedMS = point.speedMS,
                    ),
                    now = Instant.parse(point.t),
                )

                if (detected != null) {
                    actual = detected
                }

//                println(
//                    "ROAD_TEST " +
//                            "fixture=${fixture.name} " +
//                            "t=${point.t} " +
//                            "aVert=${point.aVertG} " +
//                            "detected=$detected"
//                )
            }

            assertEventEquals(fixture.name, fixture.expected, actual)
        }
    }

    private fun assertEventEquals(
        name: String,
        expected: ExpectedRoadEvent?,
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
        val actualClass = actual.eventClass ?: actual.severity
        assertEquals("$name.eventClass", expected.eventClass, actualClass)
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

    private fun loadFixtures(): List<RoadFixture> {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("motion_road_anomaly_expected.json")

        assertNotNull("Missing test resource motion_road_anomaly_expected.json", stream)

        return stream!!.use {
            json.decodeFromString<List<RoadFixture>>(
                it.bufferedReader().readText()
            )
        }
    }
}

@Serializable
data class RoadFixture(
    val name: String,
    val points: List<RoadPoint>,
    val expected: ExpectedRoadEvent? = null,
)

@Serializable
data class RoadPoint(
    val t: String,
    @SerialName("speed_m_s")
    val speedMS: Double,
    @SerialName("a_vert_g")
    val aVertG: Double,
)

@Serializable
data class ExpectedRoadEvent(
    val type: String,
    val intensity: Double,
    @SerialName("speed_m_s")
    val speedMS: Double,
    @SerialName("event_class")
    val eventClass: String,
)