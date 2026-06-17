package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStopDrainIntegrationTest {

    @Test
    fun stop_trip_stops_capture_flushes_final_batch_waits_for_delivery_then_emits_final_ui_state() {
        val capture = FakeTripCapture()
        val outbox = FakeDrainOutbox()
        val finish = FakeDrainFinishGateway()
        val ui = FakeTripUiStateSink()

        val coordinator = FakeStopDrainCoordinator(
            capture = capture,
            outbox = outbox,
            finish = finish,
            ui = ui,
        )

        capture.start()
        capture.record("motion-1")
        capture.record("motion-2")

        coordinator.stopTrip(sessionId = "session-1")

        assertFalse(capture.isRunning)
        assertEquals(
            listOf("motion-1", "motion-2"),
            outbox.enqueuedPayloads.single(),
        )
        assertEquals(1, outbox.deliveredBatchCount)
        assertEquals(listOf("session-1"), finish.finishedSessionIds)
        assertEquals(
            listOf(
                TripUiState.Stopping,
                TripUiState.FlushingFinalBatch,
                TripUiState.WaitingForDelivery,
                TripUiState.Finishing,
                TripUiState.Finished,
            ),
            ui.states,
        )
    }

    @Test
    fun finish_is_not_sent_until_final_batch_is_delivered() {
        val capture = FakeTripCapture()
        val outbox = FakeDrainOutbox(autoDeliver = false)
        val finish = FakeDrainFinishGateway()
        val ui = FakeTripUiStateSink()

        val coordinator = FakeStopDrainCoordinator(
            capture = capture,
            outbox = outbox,
            finish = finish,
            ui = ui,
        )

        capture.start()
        capture.record("motion-1")

        coordinator.stopTrip(sessionId = "session-1")

        assertTrue(finish.finishedSessionIds.isEmpty())
        assertEquals(
            listOf(
                TripUiState.Stopping,
                TripUiState.FlushingFinalBatch,
                TripUiState.WaitingForDelivery,
            ),
            ui.states,
        )

        coordinator.onFinalBatchDelivered(sessionId = "session-1")

        assertEquals(listOf("session-1"), finish.finishedSessionIds)
        assertEquals(
            listOf(
                TripUiState.Stopping,
                TripUiState.FlushingFinalBatch,
                TripUiState.WaitingForDelivery,
                TripUiState.Finishing,
                TripUiState.Finished,
            ),
            ui.states,
        )
    }

    @Test
    fun stop_trip_with_empty_buffer_still_stops_capture_and_finishes() {
        val capture = FakeTripCapture()
        val outbox = FakeDrainOutbox()
        val finish = FakeDrainFinishGateway()
        val ui = FakeTripUiStateSink()

        val coordinator = FakeStopDrainCoordinator(
            capture = capture,
            outbox = outbox,
            finish = finish,
            ui = ui,
        )

        capture.start()

        coordinator.stopTrip(sessionId = "session-1")

        assertFalse(capture.isRunning)
        assertTrue(outbox.enqueuedPayloads.isEmpty())
        assertEquals(listOf("session-1"), finish.finishedSessionIds)
        assertEquals(
            listOf(
                TripUiState.Stopping,
                TripUiState.Finishing,
                TripUiState.Finished,
            ),
            ui.states,
        )
    }
}

private enum class TripUiState {
    Stopping,
    FlushingFinalBatch,
    WaitingForDelivery,
    Finishing,
    Finished,
}

private class FakeTripCapture {
    var isRunning: Boolean = false
        private set

    private val bufferedEvents = mutableListOf<String>()

    fun start() {
        isRunning = true
    }

    fun record(event: String) {
        check(isRunning) { "capture must be running before recording events" }
        bufferedEvents += event
    }

    fun stopAndDrain(): List<String> {
        isRunning = false

        val drained = bufferedEvents.toList()
        bufferedEvents.clear()

        return drained
    }
}

private class FakeDrainOutbox(
    private val autoDeliver: Boolean = true,
) {
    val enqueuedPayloads = mutableListOf<List<String>>()

    var deliveredBatchCount: Int = 0
        private set

    fun enqueueFinalBatch(payload: List<String>): String {
        enqueuedPayloads += payload

        val batchId = "final-batch-${enqueuedPayloads.size}"

        if (autoDeliver) {
            markDelivered(batchId)
        }

        return batchId
    }

    fun markDelivered(batchId: String) {
        deliveredBatchCount += 1
    }
}

private class FakeDrainFinishGateway {
    val finishedSessionIds = mutableListOf<String>()

    fun finish(sessionId: String) {
        finishedSessionIds += sessionId
    }
}

private class FakeTripUiStateSink {
    val states = mutableListOf<TripUiState>()

    fun emit(state: TripUiState) {
        states += state
    }
}

private class FakeStopDrainCoordinator(
    private val capture: FakeTripCapture,
    private val outbox: FakeDrainOutbox,
    private val finish: FakeDrainFinishGateway,
    private val ui: FakeTripUiStateSink,
) {
    private val waitingForFinalDelivery = linkedMapOf<String, String>()

    fun stopTrip(sessionId: String) {
        ui.emit(TripUiState.Stopping)

        val finalPayload = capture.stopAndDrain()

        if (finalPayload.isEmpty()) {
            finishNow(sessionId)
            return
        }

        ui.emit(TripUiState.FlushingFinalBatch)

        val finalBatchId = outbox.enqueueFinalBatch(finalPayload)

        ui.emit(TripUiState.WaitingForDelivery)

        if (outbox.deliveredBatchCount > 0) {
            finishNow(sessionId)
        } else {
            waitingForFinalDelivery[sessionId] = finalBatchId
        }
    }

    fun onFinalBatchDelivered(sessionId: String) {
        val batchId = waitingForFinalDelivery.remove(sessionId) ?: return

        outbox.markDelivered(batchId)
        finishNow(sessionId)
    }

    private fun finishNow(sessionId: String) {
        ui.emit(TripUiState.Finishing)
        finish.finish(sessionId)
        ui.emit(TripUiState.Finished)
    }
}