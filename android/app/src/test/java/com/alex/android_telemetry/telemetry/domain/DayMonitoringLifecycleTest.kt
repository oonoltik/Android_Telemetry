package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayMonitoringLifecycleTest {

    @Test
    fun automotive_activity_auto_starts_monitored_trip_when_permissions_are_granted() {
        val permissions = FakeDayMonitoringPermissions(granted = true)
        val trips = FakeDayTripGateway()
        val ui = FakeDayMonitoringUiSink()

        val monitor = FakeDayMonitoringCoordinator(
            permissions = permissions,
            trips = trips,
            ui = ui,
        )

        monitor.onActivityChanged(ActivityType.Automotive)

        assertEquals(listOf(TripStartMode.Auto), trips.startedModes)
        assertTrue(trips.isTripRunning)
        assertEquals(
            listOf(
                DayMonitoringUiState.Monitoring,
                DayMonitoringUiState.AutoTripStarted,
            ),
            ui.states,
        )
    }

    @Test
    fun non_automotive_activity_auto_stops_auto_started_trip() {
        val permissions = FakeDayMonitoringPermissions(granted = true)
        val trips = FakeDayTripGateway()
        val ui = FakeDayMonitoringUiSink()

        val monitor = FakeDayMonitoringCoordinator(
            permissions = permissions,
            trips = trips,
            ui = ui,
        )

        monitor.onActivityChanged(ActivityType.Automotive)
        monitor.onActivityChanged(ActivityType.Walking)

        assertFalse(trips.isTripRunning)
        assertEquals(listOf(TripStopReason.NonAutomotiveActivity), trips.stopReasons)
        assertEquals(
            listOf(
                DayMonitoringUiState.Monitoring,
                DayMonitoringUiState.AutoTripStarted,
                DayMonitoringUiState.AutoTripStopping,
                DayMonitoringUiState.AutoTripStopped,
            ),
            ui.states,
        )
    }

    @Test
    fun non_automotive_activity_does_not_stop_manual_trip() {
        val permissions = FakeDayMonitoringPermissions(granted = true)
        val trips = FakeDayTripGateway()
        val ui = FakeDayMonitoringUiSink()

        trips.startTrip(mode = TripStartMode.Manual)

        val monitor = FakeDayMonitoringCoordinator(
            permissions = permissions,
            trips = trips,
            ui = ui,
        )

        monitor.onActivityChanged(ActivityType.Walking)

        assertTrue(trips.isTripRunning)
        assertEquals(TripStartMode.Manual, trips.runningMode)
        assertTrue(trips.stopReasons.isEmpty())
        assertEquals(listOf(DayMonitoringUiState.Monitoring), ui.states)
    }

    @Test
    fun automotive_activity_does_not_auto_start_when_manual_trip_is_running() {
        val permissions = FakeDayMonitoringPermissions(granted = true)
        val trips = FakeDayTripGateway()
        val ui = FakeDayMonitoringUiSink()

        trips.startTrip(mode = TripStartMode.Manual)

        val monitor = FakeDayMonitoringCoordinator(
            permissions = permissions,
            trips = trips,
            ui = ui,
        )

        monitor.onActivityChanged(ActivityType.Automotive)

        assertTrue(trips.isTripRunning)
        assertEquals(TripStartMode.Manual, trips.runningMode)
        assertEquals(listOf(TripStartMode.Manual), trips.startedModes)
        assertEquals(listOf(DayMonitoringUiState.Monitoring), ui.states)
    }

    @Test
    fun permission_denied_prevents_monitoring_and_auto_start() {
        val permissions = FakeDayMonitoringPermissions(granted = false)
        val trips = FakeDayTripGateway()
        val ui = FakeDayMonitoringUiSink()

        val monitor = FakeDayMonitoringCoordinator(
            permissions = permissions,
            trips = trips,
            ui = ui,
        )

        monitor.onActivityChanged(ActivityType.Automotive)

        assertFalse(trips.isTripRunning)
        assertTrue(trips.startedModes.isEmpty())
        assertEquals(
            listOf(DayMonitoringUiState.PermissionDenied),
            ui.states,
        )
    }

    @Test
    fun permission_revoked_while_auto_trip_running_stops_auto_trip_and_blocks_future_auto_start() {
        val permissions = FakeDayMonitoringPermissions(granted = true)
        val trips = FakeDayTripGateway()
        val ui = FakeDayMonitoringUiSink()

        val monitor = FakeDayMonitoringCoordinator(
            permissions = permissions,
            trips = trips,
            ui = ui,
        )

        monitor.onActivityChanged(ActivityType.Automotive)

        permissions.granted = false

        monitor.onPermissionsChanged()
        monitor.onActivityChanged(ActivityType.Automotive)

        assertFalse(trips.isTripRunning)
        assertEquals(
            listOf(TripStopReason.PermissionRevoked),
            trips.stopReasons,
        )
        assertEquals(
            listOf(
                DayMonitoringUiState.Monitoring,
                DayMonitoringUiState.AutoTripStarted,
                DayMonitoringUiState.PermissionDenied,
                DayMonitoringUiState.AutoTripStopping,
                DayMonitoringUiState.AutoTripStopped,
                DayMonitoringUiState.PermissionDenied,
            ),
            ui.states,
        )
    }
}

private enum class ActivityType {
    Automotive,
    Walking,
    Still,
    Unknown,
}

private enum class TripStartMode {
    Auto,
    Manual,
}

private enum class TripStopReason {
    NonAutomotiveActivity,
    PermissionRevoked,
}

private enum class DayMonitoringUiState {
    Monitoring,
    AutoTripStarted,
    AutoTripStopping,
    AutoTripStopped,
    PermissionDenied,
}

private class FakeDayMonitoringPermissions(
    var granted: Boolean,
) {
    fun canMonitorDay(): Boolean {
        return granted
    }
}

private class FakeDayTripGateway {
    val startedModes = mutableListOf<TripStartMode>()
    val stopReasons = mutableListOf<TripStopReason>()

    var isTripRunning: Boolean = false
        private set

    var runningMode: TripStartMode? = null
        private set

    fun startTrip(mode: TripStartMode) {
        if (isTripRunning) return

        isTripRunning = true
        runningMode = mode
        startedModes += mode
    }

    fun stopTrip(reason: TripStopReason) {
        if (!isTripRunning) return

        isTripRunning = false
        runningMode = null
        stopReasons += reason
    }
}

private class FakeDayMonitoringUiSink {
    val states = mutableListOf<DayMonitoringUiState>()

    fun emit(state: DayMonitoringUiState) {
        states += state
    }
}

private class FakeDayMonitoringCoordinator(
    private val permissions: FakeDayMonitoringPermissions,
    private val trips: FakeDayTripGateway,
    private val ui: FakeDayMonitoringUiSink,
) {
    private var monitoringEmitted: Boolean = false
    fun onActivityChanged(activityType: ActivityType) {
        if (!permissions.canMonitorDay()) {
            ui.emit(DayMonitoringUiState.PermissionDenied)
            return
        }

        emitMonitoringOnce()

        when (activityType) {
            ActivityType.Automotive -> maybeAutoStartTrip()
            ActivityType.Walking,
            ActivityType.Still,
            ActivityType.Unknown -> maybeAutoStopTrip()
        }
    }

    fun onPermissionsChanged() {
        if (permissions.canMonitorDay()) return

        ui.emit(DayMonitoringUiState.PermissionDenied)

        if (trips.isTripRunning && trips.runningMode == TripStartMode.Auto) {
            ui.emit(DayMonitoringUiState.AutoTripStopping)
            trips.stopTrip(TripStopReason.PermissionRevoked)
            ui.emit(DayMonitoringUiState.AutoTripStopped)
        }
    }

    private fun emitMonitoringOnce() {
        if (monitoringEmitted) return

        monitoringEmitted = true
        ui.emit(DayMonitoringUiState.Monitoring)
    }

    private fun maybeAutoStartTrip() {
        if (trips.isTripRunning) return

        trips.startTrip(mode = TripStartMode.Auto)
        ui.emit(DayMonitoringUiState.AutoTripStarted)
    }

    private fun maybeAutoStopTrip() {
        if (!trips.isTripRunning) return
        if (trips.runningMode != TripStartMode.Auto) return

        ui.emit(DayMonitoringUiState.AutoTripStopping)
        trips.stopTrip(TripStopReason.NonAutomotiveActivity)
        ui.emit(DayMonitoringUiState.AutoTripStopped)
    }
}