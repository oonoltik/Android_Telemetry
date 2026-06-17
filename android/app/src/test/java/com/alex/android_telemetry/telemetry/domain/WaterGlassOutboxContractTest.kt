package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterGlassOutboxContractTest {

    @Test
    fun waterglass_samples_are_enqueued_locally_and_survive_restart() {
        val disk = linkedMapOf<String, PersistedWaterGlassSample>()

        val beforeRestart = FakeWaterGlassOutboxStore(disk)

        beforeRestart.enqueue(
            sampleId = "wg-1",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:00Z",
            payloadJson = """{"score":0.91}""",
        )

        val afterRestart = FakeWaterGlassOutboxStore(disk)

        assertEquals(
            listOf(
                PersistedWaterGlassSample(
                    sampleId = "wg-1",
                    sessionId = "session-1",
                    capturedAtIsoUtc = "2025-01-01T10:00:00Z",
                    payloadJson = """{"score":0.91}""",
                )
            ),
            afterRestart.pendingOrdered(),
        )
    }

    @Test
    fun failed_waterglass_delivery_keeps_sample_pending_and_tracks_retry_metadata() {
        val disk = linkedMapOf<String, PersistedWaterGlassSample>()
        val outbox = FakeWaterGlassOutboxStore(disk)

        outbox.enqueue(
            sampleId = "wg-1",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:00Z",
            payloadJson = """{"score":0.91}""",
        )

        outbox.markFailed(
            sampleId = "wg-1",
            error = "HTTP 503",
        )

        val afterRestart = FakeWaterGlassOutboxStore(disk)
        val pending = afterRestart.pendingOrdered().single()

        assertEquals("wg-1", pending.sampleId)
        assertEquals(1, pending.retryCount)
        assertEquals("HTTP 503", pending.lastError)
    }

    @Test
    fun successful_waterglass_delivery_removes_sample_from_outbox() {
        val disk = linkedMapOf<String, PersistedWaterGlassSample>()
        val outbox = FakeWaterGlassOutboxStore(disk)

        outbox.enqueue(
            sampleId = "wg-1",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:00Z",
            payloadJson = """{"score":0.91}""",
        )

        outbox.markDelivered(sampleId = "wg-1")

        val afterRestart = FakeWaterGlassOutboxStore(disk)

        assertTrue(afterRestart.pendingOrdered().isEmpty())
        assertTrue(disk.isEmpty())
    }

    @Test
    fun waterglass_outbox_is_separate_from_telemetry_batch_outbox() {
        val telemetryDisk = linkedMapOf<String, PersistedTelemetryBatchForWaterGlassIsolation>()
        val waterGlassDisk = linkedMapOf<String, PersistedWaterGlassSample>()

        val telemetryOutbox = FakeTelemetryOutboxForWaterGlassIsolation(telemetryDisk)
        val waterGlassOutbox = FakeWaterGlassOutboxStore(waterGlassDisk)

        telemetryOutbox.enqueue(
            batchId = "batch-1",
            sessionId = "session-1",
            batchSeq = 1,
            payloadJson = """{"batch_seq":1}""",
        )
        waterGlassOutbox.enqueue(
            sampleId = "wg-1",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:00Z",
            payloadJson = """{"score":0.91}""",
        )

        waterGlassOutbox.markDelivered(sampleId = "wg-1")

        assertEquals(listOf("batch-1"), telemetryOutbox.pendingIds())
        assertTrue(waterGlassOutbox.pendingOrdered().isEmpty())
    }

    @Test
    fun waterglass_retry_order_is_by_capture_time_then_sample_id() {
        val disk = linkedMapOf<String, PersistedWaterGlassSample>()
        val outbox = FakeWaterGlassOutboxStore(disk)

        outbox.enqueue(
            sampleId = "wg-3",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:03Z",
            payloadJson = """{"score":0.3}""",
        )
        outbox.enqueue(
            sampleId = "wg-1b",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:01Z",
            payloadJson = """{"score":0.11}""",
        )
        outbox.enqueue(
            sampleId = "wg-1a",
            sessionId = "session-1",
            capturedAtIsoUtc = "2025-01-01T10:00:01Z",
            payloadJson = """{"score":0.10}""",
        )

        assertEquals(
            listOf("wg-1a", "wg-1b", "wg-3"),
            outbox.pendingOrdered().map { it.sampleId },
        )
    }
}

private data class PersistedWaterGlassSample(
    val sampleId: String,
    val sessionId: String,
    val capturedAtIsoUtc: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val lastError: String? = null,
)

private class FakeWaterGlassOutboxStore(
    private val disk: LinkedHashMap<String, PersistedWaterGlassSample>,
) {
    fun enqueue(
        sampleId: String,
        sessionId: String,
        capturedAtIsoUtc: String,
        payloadJson: String,
    ) {
        disk[sampleId] = PersistedWaterGlassSample(
            sampleId = sampleId,
            sessionId = sessionId,
            capturedAtIsoUtc = capturedAtIsoUtc,
            payloadJson = payloadJson,
        )
    }

    fun pendingOrdered(): List<PersistedWaterGlassSample> {
        return disk.values.sortedWith(
            compareBy<PersistedWaterGlassSample> { it.capturedAtIsoUtc }
                .thenBy { it.sampleId }
        )
    }

    fun markFailed(
        sampleId: String,
        error: String,
    ) {
        val current = disk[sampleId] ?: return

        disk[sampleId] = current.copy(
            retryCount = current.retryCount + 1,
            lastError = error,
        )
    }

    fun markDelivered(sampleId: String) {
        disk.remove(sampleId)
    }
}

private data class PersistedTelemetryBatchForWaterGlassIsolation(
    val batchId: String,
    val sessionId: String,
    val batchSeq: Int,
    val payloadJson: String,
)

private class FakeTelemetryOutboxForWaterGlassIsolation(
    private val disk: LinkedHashMap<String, PersistedTelemetryBatchForWaterGlassIsolation>,
) {
    fun enqueue(
        batchId: String,
        sessionId: String,
        batchSeq: Int,
        payloadJson: String,
    ) {
        disk[batchId] = PersistedTelemetryBatchForWaterGlassIsolation(
            batchId = batchId,
            sessionId = sessionId,
            batchSeq = batchSeq,
            payloadJson = payloadJson,
        )
    }

    fun pendingIds(): List<String> {
        return disk.keys.toList()
    }
}