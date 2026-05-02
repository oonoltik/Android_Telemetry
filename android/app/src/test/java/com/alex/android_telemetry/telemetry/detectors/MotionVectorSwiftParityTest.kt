package com.alex.android_telemetry.telemetry.detectors

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MotionVectorSwiftParityTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    @Test
    fun swift_trace_matches_android_projection() {
        val fixtures = loadFixtures()
        assert(fixtures.isNotEmpty())

        val computer = MotionVectorComputer()

        fixtures.forEach { fixture ->
            val actual = computer.computeProjected(
                accelRefNorthG = fixture.input.accelRefNorthG,
                accelRefEastG = fixture.input.accelRefEastG,
                accelRefUpG = fixture.input.accelRefUpG,
                speedMS = fixture.input.speedMS,
                courseRad = fixture.input.courseRad,
                imuForwardAxisRefNorth = fixture.input.imuForwardAxisRefNorth,
                imuForwardAxisRefEast = fixture.input.imuForwardAxisRefEast,
                preferGpsProjection = fixture.input.preferGpsProjection,
            )

            assertAlmostEquals("${fixture.name}.aLongG", fixture.expected.aLongG, actual.aLongG)
            assertAlmostEquals("${fixture.name}.aLatG", fixture.expected.aLatG, actual.aLatG)
            assertAlmostEquals("${fixture.name}.aVertG", fixture.expected.aVertG, actual.aVertG)
        }
    }

    private fun loadFixtures(): List<SwiftParityFixture> {
        val stream = javaClass.classLoader?.getResourceAsStream("motion_vector_swift_parity_trace.json")
        assertNotNull("Missing test resource motion_vector_swift_parity_trace.json", stream)
        return stream!!.use {
            json.decodeFromString<List<SwiftParityFixture>>(it.bufferedReader().readText())
        }
    }

    private fun assertAlmostEquals(
        label: String,
        expected: Double?,
        actual: Double?,
        tolerance: Double = 1e-3,
    ) {
        if (expected == null || actual == null) {
            assertEquals(label, expected, actual)
            return
        }
        assertEquals(label, expected, actual, tolerance)
    }
}

@Serializable
data class SwiftParityFixture(
    val name: String,
    val input: SwiftParityInput,
    val expected: SwiftParityExpected,
)

@Serializable
data class SwiftParityInput(
    val accelRefNorthG: Double? = null,
    val accelRefEastG: Double? = null,
    val accelRefUpG: Double? = null,
    val speedMS: Double? = null,
    val courseRad: Double? = null,
    val imuForwardAxisRefNorth: Double? = null,
    val imuForwardAxisRefEast: Double? = null,
    val preferGpsProjection: Boolean = false,
)

@Serializable
data class SwiftParityExpected(
    val aLongG: Double? = null,
    val aLatG: Double? = null,
    val aVertG: Double? = null,
)