package com.alex.android_telemetry.telemetry.detectors

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MotionVectorComputerParityTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    @Test
    fun golden_projection_cases_match_expected_output() {
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
            assertAlmostEquals("${fixture.name}.speedMS", fixture.expected.speedMS, actual.speedMS)
        }
    }

    private fun loadFixtures(): List<MotionVectorFixture> {
        val stream = javaClass.classLoader?.getResourceAsStream("motion_vector_golden.json")
        assertNotNull("Missing test resource motion_vector_golden.json", stream)
        return stream!!.use {
            json.decodeFromString<List<MotionVectorFixture>>(it.bufferedReader().readText())
        }
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
}

@Serializable
data class MotionVectorFixture(
    val name: String,
    val input: MotionVectorFixtureInput,
    val expected: MotionVectorFixtureExpected,
)

@Serializable
data class MotionVectorFixtureInput(
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
data class MotionVectorFixtureExpected(
    val aLongG: Double? = null,
    val aLatG: Double? = null,
    val aVertG: Double? = null,
    val speedMS: Double? = null,
)