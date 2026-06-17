package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryServicePolicyTest {

    @Test
    fun foreground_service_owns_sensor_capture_lifecycle() {
        val sensors = FakeSensorCaptureGateway()
        val service = FakeTelemetryForegroundService(sensors)
        val ui = FakeTelemetryUiController(service)

        ui.onStartTripClicked()

        assertEquals(listOf("startForeground", "startSensors"), service.events)
        assertTrue(sensors.isCapturing)

        ui.onStopTripClicked()

        assertEquals(
            listOf(
                "startForeground",
                "startSensors",
                "stopSensors",
                "stopForeground",
            ),
            service.events,
        )
        assertTrue(sensors.isCapturing.not())
    }

    @Test
    fun ui_never_controls_sensors_directly() {
        val sensors = FakeSensorCaptureGateway()
        val service = FakeTelemetryForegroundService(sensors)
        val ui = FakeTelemetryUiController(service)

        ui.onStartTripClicked()
        ui.onStopTripClicked()

        assertTrue(ui.directSensorCalls.isEmpty())
        assertEquals(
            listOf(
                "startSensors",
                "stopSensors",
            ),
            sensors.calls,
        )
    }

    @Test
    fun service_start_stop_is_idempotent_to_prevent_lifecycle_spam() {
        val sensors = FakeSensorCaptureGateway()
        val service = FakeTelemetryForegroundService(sensors)

        service.startTripCapture()
        service.startTripCapture()
        service.startTripCapture()

        service.stopTripCapture()
        service.stopTripCapture()

        assertEquals(
            listOf(
                "startForeground",
                "startSensors",
                "stopSensors",
                "stopForeground",
            ),
            service.events,
        )
        assertEquals(
            listOf(
                "startSensors",
                "stopSensors",
            ),
            sensors.calls,
        )
    }
}

private class FakeSensorCaptureGateway {
    val calls = mutableListOf<String>()

    var isCapturing: Boolean = false
        private set

    fun start() {
        if (isCapturing) return

        isCapturing = true
        calls += "startSensors"
    }

    fun stop() {
        if (!isCapturing) return

        isCapturing = false
        calls += "stopSensors"
    }
}

private class FakeTelemetryForegroundService(
    private val sensors: FakeSensorCaptureGateway,
) {
    val events = mutableListOf<String>()

    private var foreground: Boolean = false

    fun startTripCapture() {
        if (foreground) return

        foreground = true
        events += "startForeground"

        sensors.start()
        events += "startSensors"
    }

    fun stopTripCapture() {
        if (!foreground) return

        sensors.stop()
        events += "stopSensors"

        foreground = false
        events += "stopForeground"
    }
}

private class FakeTelemetryUiController(
    private val service: FakeTelemetryForegroundService,
) {
    val directSensorCalls = mutableListOf<String>()

    fun onStartTripClicked() {
        service.startTripCapture()
    }

    fun onStopTripClicked() {
        service.stopTripCapture()
    }
}