package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverAccountLifecycleTest {

    @Test
    fun changing_driver_id_finishes_current_session_and_starts_new_session_for_new_driver() {
        val sessions = FakeDriverSessionStore()
        val outbox = FakeDriverBatchOutbox()
        val finish = FakeDriverFinishGateway()

        val lifecycle = FakeDriverAccountLifecycleCoordinator(
            sessions = sessions,
            outbox = outbox,
            finish = finish,
        )

        lifecycle.setDriver("driver-old")
        lifecycle.startTrip(sessionId = "session-old")
        lifecycle.recordTelemetry("motion-old-1")

        lifecycle.setDriver("driver-new")
        lifecycle.startTrip(sessionId = "session-new")
        lifecycle.recordTelemetry("motion-new-1")

        assertEquals(listOf("session-old"), finish.finishedSessionIds)
        assertEquals("driver-new", sessions.currentDriverId)
        assertEquals("session-new", sessions.currentSessionId)

        assertEquals(
            listOf(
                PersistedDriverBatch(
                    sessionId = "session-old",
                    driverId = "driver-old",
                    payload = "motion-old-1",
                ),
                PersistedDriverBatch(
                    sessionId = "session-new",
                    driverId = "driver-new",
                    payload = "motion-new-1",
                ),
            ),
            outbox.batches,
        )
    }

    @Test
    fun new_driver_id_is_written_to_new_batches_after_driver_switch() {
        val sessions = FakeDriverSessionStore()
        val outbox = FakeDriverBatchOutbox()
        val finish = FakeDriverFinishGateway()

        val lifecycle = FakeDriverAccountLifecycleCoordinator(
            sessions = sessions,
            outbox = outbox,
            finish = finish,
        )

        lifecycle.setDriver("driver-a")
        lifecycle.startTrip(sessionId = "session-a")
        lifecycle.recordTelemetry("a-1")

        lifecycle.setDriver("driver-b")
        lifecycle.startTrip(sessionId = "session-b")
        lifecycle.recordTelemetry("b-1")
        lifecycle.recordTelemetry("b-2")

        assertEquals(
            listOf("driver-a", "driver-b", "driver-b"),
            outbox.batches.map { it.driverId },
        )
        assertEquals(
            listOf("session-a", "session-b", "session-b"),
            outbox.batches.map { it.sessionId },
        )
    }

    @Test
    fun delete_account_clears_local_state_and_prevents_stale_replay() {
        val sessions = FakeDriverSessionStore()
        val outbox = FakeDriverBatchOutbox()
        val finish = FakeDriverFinishGateway()
        val pendingFinish = FakeDriverPendingFinishStore()
        val deliveryStats = FakeDriverDeliveryStatsStore()

        val lifecycle = FakeDriverAccountLifecycleCoordinator(
            sessions = sessions,
            outbox = outbox,
            finish = finish,
            pendingFinish = pendingFinish,
            deliveryStats = deliveryStats,
        )

        lifecycle.setDriver("driver-a")
        lifecycle.startTrip(sessionId = "session-a")
        lifecycle.recordTelemetry("a-1")

        pendingFinish.add("session-a")
        deliveryStats.setDelivered(sessionId = "session-a", delivered = 1)

        lifecycle.deleteAccount()

        assertEquals(null, sessions.currentDriverId)
        assertEquals(null, sessions.currentSessionId)
        assertTrue(outbox.batches.isEmpty())
        assertTrue(pendingFinish.sessionIds.isEmpty())
        assertTrue(deliveryStats.deliveredBySession.isEmpty())

        lifecycle.setDriver("driver-b")
        lifecycle.startTrip(sessionId = "session-b")
        lifecycle.recordTelemetry("b-1")

        assertEquals(
            listOf(
                PersistedDriverBatch(
                    sessionId = "session-b",
                    driverId = "driver-b",
                    payload = "b-1",
                ),
            ),
            outbox.batches,
        )
    }

    @Test
    fun stale_batches_from_previous_driver_are_not_replayed_for_new_driver() {
        val sessions = FakeDriverSessionStore()
        val outbox = FakeDriverBatchOutbox()
        val finish = FakeDriverFinishGateway()

        val lifecycle = FakeDriverAccountLifecycleCoordinator(
            sessions = sessions,
            outbox = outbox,
            finish = finish,
        )

        lifecycle.setDriver("driver-a")
        lifecycle.startTrip(sessionId = "session-a")
        lifecycle.recordTelemetry("a-1")

        lifecycle.setDriver("driver-b")
        lifecycle.startTrip(sessionId = "session-b")
        lifecycle.recordTelemetry("b-1")

        val replayForDriverB = outbox.pendingForDriver("driver-b")

        assertEquals(
            listOf(
                PersistedDriverBatch(
                    sessionId = "session-b",
                    driverId = "driver-b",
                    payload = "b-1",
                ),
            ),
            replayForDriverB,
        )
    }
}

private data class PersistedDriverBatch(
    val sessionId: String,
    val driverId: String,
    val payload: String,
)

private class FakeDriverSessionStore {
    var currentDriverId: String? = null
        private set

    var currentSessionId: String? = null
        private set

    fun setDriver(driverId: String?) {
        currentDriverId = driverId
    }

    fun setSession(sessionId: String?) {
        currentSessionId = sessionId
    }

    fun clear() {
        currentDriverId = null
        currentSessionId = null
    }
}

private class FakeDriverBatchOutbox {
    val batches = mutableListOf<PersistedDriverBatch>()

    fun enqueue(
        sessionId: String,
        driverId: String,
        payload: String,
    ) {
        batches += PersistedDriverBatch(
            sessionId = sessionId,
            driverId = driverId,
            payload = payload,
        )
    }

    fun pendingForDriver(driverId: String): List<PersistedDriverBatch> {
        return batches.filter { it.driverId == driverId }
    }

    fun clear() {
        batches.clear()
    }
}

private class FakeDriverFinishGateway {
    val finishedSessionIds = mutableListOf<String>()

    fun finish(sessionId: String) {
        finishedSessionIds += sessionId
    }
}

private class FakeDriverPendingFinishStore {
    val sessionIds = mutableSetOf<String>()

    fun add(sessionId: String) {
        sessionIds += sessionId
    }

    fun clear() {
        sessionIds.clear()
    }
}

private class FakeDriverDeliveryStatsStore {
    val deliveredBySession = linkedMapOf<String, Int>()

    fun setDelivered(sessionId: String, delivered: Int) {
        deliveredBySession[sessionId] = delivered
    }

    fun clear() {
        deliveredBySession.clear()
    }
}

private class FakeDriverAccountLifecycleCoordinator(
    private val sessions: FakeDriverSessionStore,
    private val outbox: FakeDriverBatchOutbox,
    private val finish: FakeDriverFinishGateway,
    private val pendingFinish: FakeDriverPendingFinishStore = FakeDriverPendingFinishStore(),
    private val deliveryStats: FakeDriverDeliveryStatsStore = FakeDriverDeliveryStatsStore(),
) {
    fun setDriver(driverId: String) {
        val oldDriverId = sessions.currentDriverId
        val oldSessionId = sessions.currentSessionId

        if (oldDriverId != null && oldDriverId != driverId && oldSessionId != null) {
            finish.finish(oldSessionId)
            sessions.setSession(null)
        }

        sessions.setDriver(driverId)
    }

    fun startTrip(sessionId: String) {
        checkNotNull(sessions.currentDriverId) { "driver_id must be set before starting trip" }

        sessions.setSession(sessionId)
    }

    fun recordTelemetry(payload: String) {
        val driverId = checkNotNull(sessions.currentDriverId) {
            "driver_id must be set before telemetry is recorded"
        }
        val sessionId = checkNotNull(sessions.currentSessionId) {
            "session_id must be set before telemetry is recorded"
        }

        outbox.enqueue(
            sessionId = sessionId,
            driverId = driverId,
            payload = payload,
        )
    }

    fun deleteAccount() {
        val activeSessionId = sessions.currentSessionId

        if (activeSessionId != null) {
            finish.finish(activeSessionId)
        }

        sessions.clear()
        outbox.clear()
        pendingFinish.clear()
        deliveryStats.clear()
    }
}