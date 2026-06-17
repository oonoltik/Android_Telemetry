package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayMonitoringDisableParityTest {

    @Test
    fun disable_stops_owned_auto_trip() {
        val trips = FakeTrips()
        val manager = FakeDayMonitoringDisableCoordinator(trips)

        manager.enable()
        manager.autoStartTrip("session-1")

        manager.disable()

        assertFalse(trips.isRunning)
        assertEquals(listOf("session-1"), trips.stoppedSessions)
    }

    @Test
    fun disable_does_not_stop_manual_trip() {
        val trips = FakeTrips()
        val manager = FakeDayMonitoringDisableCoordinator(trips)

        manager.enable()
        trips.startManualTrip("manual-session")

        manager.disable()

        assertTrue(trips.isRunning)
        assertEquals("manual-session", trips.currentSessionId)
        assertTrue(trips.stoppedSessions.isEmpty())
    }

    @Test
    fun disable_does_not_stop_foreign_active_trip() {
        val trips = FakeTrips()
        val manager = FakeDayMonitoringDisableCoordinator(trips)

        manager.enable()
        manager.autoStartTrip("auto-session")
        trips.replaceWithManualOrForeignTrip("foreign-session")

        manager.disable()

        assertTrue(trips.isRunning)
        assertEquals("foreign-session", trips.currentSessionId)
        assertTrue(trips.stoppedSessions.isEmpty())
    }
}

private class FakeDayMonitoringDisableCoordinator(
    private val trips: FakeTrips,
) {
    private var enabled: Boolean = false
    private var autoStartedTripActive: Boolean = false
    private var autoStartedSessionId: String? = null

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false

        val currentSessionId = trips.currentSessionId

        if (
            autoStartedTripActive &&
            currentSessionId != null &&
            autoStartedSessionId == currentSessionId
        ) {
            trips.stopTrip(currentSessionId)
        }

        autoStartedTripActive = false
        autoStartedSessionId = null
    }

    fun autoStartTrip(sessionId: String) {
        trips.startAutoTrip(sessionId)
        autoStartedTripActive = true
        autoStartedSessionId = sessionId
    }
}

private class FakeTrips {
    val stoppedSessions = mutableListOf<String>()

    var isRunning: Boolean = false
        private set

    var currentSessionId: String? = null
        private set

    fun startAutoTrip(sessionId: String) {
        isRunning = true
        currentSessionId = sessionId
    }

    fun startManualTrip(sessionId: String) {
        isRunning = true
        currentSessionId = sessionId
    }

    fun replaceWithManualOrForeignTrip(sessionId: String) {
        isRunning = true
        currentSessionId = sessionId
    }

    fun stopTrip(sessionId: String) {
        if (!isRunning) return
        if (currentSessionId != sessionId) return

        stoppedSessions += sessionId
        isRunning = false
        currentSessionId = null
    }
}