package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryBatchOutboxPersistenceTest {

    @Test
    fun enqueue_persists_pending_batches_across_restart() {
        val disk = linkedMapOf<String, PersistedOutboxBatch>()

        val beforeRestart = FakeTelemetryBatchOutboxStore(disk)

        beforeRestart.enqueue(
            sessionId = "session-1",
            batchId = "batch-1",
            batchSeq = 1,
            payloadJson = """{"batch_seq":1}""",
        )

        val afterRestart = FakeTelemetryBatchOutboxStore(disk)

        assertEquals(
            listOf("batch-1"),
            afterRestart.pendingOrdered().map { it.batchId },
        )
        assertEquals(
            """{"batch_seq":1}""",
            afterRestart.pendingOrdered().single().payloadJson,
        )
    }

    @Test
    fun failed_delivery_keeps_batch_pending_and_persists_retry_metadata() {
        val disk = linkedMapOf<String, PersistedOutboxBatch>()

        val store = FakeTelemetryBatchOutboxStore(disk)

        store.enqueue(
            sessionId = "session-1",
            batchId = "batch-1",
            batchSeq = 1,
            payloadJson = """{"batch_seq":1}""",
        )

        store.markFailed(
            batchId = "batch-1",
            error = "HTTP 500",
        )

        val afterRestart = FakeTelemetryBatchOutboxStore(disk)
        val pending = afterRestart.pendingOrdered().single()

        assertEquals("batch-1", pending.batchId)
        assertEquals(1, pending.attemptCount)
        assertEquals("HTTP 500", pending.lastError)
        assertTrue(pending.delivered.not())
    }

    @Test
    fun successful_delivery_removes_batch_from_outbox_across_restart() {
        val disk = linkedMapOf<String, PersistedOutboxBatch>()

        val store = FakeTelemetryBatchOutboxStore(disk)

        store.enqueue(
            sessionId = "session-1",
            batchId = "batch-1",
            batchSeq = 1,
            payloadJson = """{"batch_seq":1}""",
        )

        store.markDelivered(batchId = "batch-1")

        val afterRestart = FakeTelemetryBatchOutboxStore(disk)

        assertTrue(afterRestart.pendingOrdered().isEmpty())
        assertTrue(disk.isEmpty())
    }

    @Test
    fun pending_batches_are_replayed_by_batch_seq_then_batch_id() {
        val disk = linkedMapOf<String, PersistedOutboxBatch>()

        val store = FakeTelemetryBatchOutboxStore(disk)

        store.enqueue(
            sessionId = "session-1",
            batchId = "batch-3",
            batchSeq = 3,
            payloadJson = """{"batch_seq":3}""",
        )
        store.enqueue(
            sessionId = "session-1",
            batchId = "batch-1",
            batchSeq = 1,
            payloadJson = """{"batch_seq":1}""",
        )
        store.enqueue(
            sessionId = "session-1",
            batchId = "batch-2b",
            batchSeq = 2,
            payloadJson = """{"batch_seq":2,"variant":"b"}""",
        )
        store.enqueue(
            sessionId = "session-1",
            batchId = "batch-2a",
            batchSeq = 2,
            payloadJson = """{"batch_seq":2,"variant":"a"}""",
        )

        val afterRestart = FakeTelemetryBatchOutboxStore(disk)

        assertEquals(
            listOf(
                "batch-1",
                "batch-2a",
                "batch-2b",
                "batch-3",
            ),
            afterRestart.pendingOrdered().map { it.batchId },
        )
    }
}

private data class PersistedOutboxBatch(
    val sessionId: String,
    val batchId: String,
    val batchSeq: Int,
    val payloadJson: String,
    val delivered: Boolean = false,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

private class FakeTelemetryBatchOutboxStore(
    private val disk: LinkedHashMap<String, PersistedOutboxBatch>,
) {
    fun enqueue(
        sessionId: String,
        batchId: String,
        batchSeq: Int,
        payloadJson: String,
    ) {
        disk[batchId] = PersistedOutboxBatch(
            sessionId = sessionId,
            batchId = batchId,
            batchSeq = batchSeq,
            payloadJson = payloadJson,
        )
    }

    fun pendingOrdered(): List<PersistedOutboxBatch> {
        return disk.values
            .filterNot { it.delivered }
            .sortedWith(
                compareBy<PersistedOutboxBatch> { it.batchSeq }
                    .thenBy { it.batchId }
            )
    }

    fun markFailed(
        batchId: String,
        error: String,
    ) {
        val current = disk[batchId] ?: return

        disk[batchId] = current.copy(
            attemptCount = current.attemptCount + 1,
            lastError = error,
            delivered = false,
        )
    }

    fun markDelivered(batchId: String) {
        disk.remove(batchId)
    }
}