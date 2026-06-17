package com.alex.android_telemetry.telemetry.domain

import com.alex.android_telemetry.telemetry.trips.api.ClientAggDto
import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.DriverHomeResponseDto
import com.alex.android_telemetry.telemetry.trips.api.FinishCommand
import com.alex.android_telemetry.telemetry.trips.api.PendingTripFinishDto
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripApiException
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryDto
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryGateway
import com.alex.android_telemetry.telemetry.trips.storage.PendingTripFinishGateway
import com.alex.android_telemetry.telemetry.trips.storage.TripDeliveryStatsReader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFinishManagerLifecycleTest {

    @Test
    fun delivered_zero_queues_finish_and_does_not_call_api() = runBlocking {
        val api = FakeTripApi()
        val store = FakePendingStore()
        val stats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()

        stats.setDelivered("session-1", 0)

        val manager = TripFinishManager(
            tripApi = api,
            pendingStore = store,
            deliveryStatsStore = stats,
            finishRetryScheduler = scheduler,
        )

        val result = manager.finishTrip(finishCommand())

        assertTrue(result is TripFinishResult.Queued)
        assertEquals(0, api.finishCalls)
        assertEquals(1, scheduler.scheduleCalls)

        val pending = store.getBySessionId("session-1")
        requireNotNull(pending)

        assertTrue(pending.queuedBecauseNoDeliveredBatches)
        assertEquals("No delivered ingest batches yet", pending.lastError)
        assertEquals("session-1", pending.sessionId)
        assertEquals("driver-1", pending.driverId)
        assertEquals("device-1", pending.deviceId)
    }

    @Test
    fun delivered_positive_sends_finish_and_removes_pending() = runBlocking {
        val api = FakeTripApi()
        val store = FakePendingStore()
        val stats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()

        stats.setDelivered("session-1", 1)

        val manager = TripFinishManager(
            tripApi = api,
            pendingStore = store,
            deliveryStatsStore = stats,
            finishRetryScheduler = scheduler,
        )

        val result = manager.finishTrip(finishCommand())

        assertTrue(result is TripFinishResult.Sent)
        assertEquals(1, api.finishCalls)
        assertEquals(0, scheduler.scheduleCalls)
        assertFalse(store.exists("session-1"))

        val sent = api.lastPending
        requireNotNull(sent)
        assertFalse(sent.queuedBecauseNoDeliveredBatches)
        assertEquals("session-1", sent.sessionId)
    }

    @Test

    fun retryable_finish_error_stores_pending_and_schedules_retry() = runBlocking {
        val api = FakeTripApi()
        val store = FakePendingStore()
        val stats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()

        stats.setDelivered("session-1", 1)
        api.failWith = TripApiException(
            code = 500,
            message = "HTTP 500",
        )

        val manager = TripFinishManager(
            tripApi = api,
            pendingStore = store,
            deliveryStatsStore = stats,
            finishRetryScheduler = scheduler,
        )

        val result = manager.finishTrip(finishCommand())

        assertTrue(result is TripFinishResult.Queued)
        assertEquals(1, api.finishCalls)
        assertEquals(2, scheduler.scheduleCalls)

        val pending = store.getBySessionId("session-1")
        requireNotNull(pending)

        assertFalse(pending.queuedBecauseNoDeliveredBatches)
        assertEquals(0, pending.retryCount)
        assertTrue(pending.lastError?.contains("HTTP 500") == true)
    }

    @Test
    fun retry_pending_skips_when_no_delivered_batches() = runBlocking {
        val api = FakeTripApi()
        val store = FakePendingStore()
        val stats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()

        store.upsert(pendingFinish(queuedBecauseNoDeliveredBatches = true))
        stats.setDelivered("session-1", 0)

        val manager = TripFinishManager(
            tripApi = api,
            pendingStore = store,
            deliveryStatsStore = stats,
            finishRetryScheduler = scheduler,
        )

        manager.retryPendingFinishes()

        assertEquals(0, api.finishCalls)
        assertTrue(store.exists("session-1"))
        assertEquals(0, scheduler.scheduleCalls)
    }

    @Test
    fun first_delivered_batch_retry_sends_pending_and_removes_it() = runBlocking {
        val api = FakeTripApi()
        val store = FakePendingStore()
        val stats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()

        store.upsert(pendingFinish(queuedBecauseNoDeliveredBatches = true))
        stats.setDelivered("session-1", 1)

        val manager = TripFinishManager(
            tripApi = api,
            pendingStore = store,
            deliveryStatsStore = stats,
            finishRetryScheduler = scheduler,
        )

        manager.retryPendingFinishes()

        assertEquals(1, api.finishCalls)
        assertFalse(store.exists("session-1"))

        val sent = api.lastPending
        requireNotNull(sent)

        assertFalse(sent.queuedBecauseNoDeliveredBatches)
    }

    @Test
    fun restart_recovery_replays_pending_batches_before_pending_finish_and_dedups_second_pass() = runBlocking {
        val api = FakeTripApi()
        val persistentPendingStore = FakePendingStore()
        val persistentDeliveryStats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()
        val outbox = FakePendingBatchOutbox()

        outbox.enqueue(sessionId = "session-1", batchId = "batch-1")
        persistentDeliveryStats.setDelivered("session-1", 0)

        val beforeRestartManager = TripFinishManager(
            tripApi = api,
            pendingStore = persistentPendingStore,
            deliveryStatsStore = persistentDeliveryStats,
            finishRetryScheduler = scheduler,
        )

        val queued = beforeRestartManager.finishTrip(finishCommand())

        assertTrue(queued is TripFinishResult.Queued)
        assertTrue(persistentPendingStore.exists("session-1"))
        assertEquals(0, api.finishCalls)

        val afterRestartManager = TripFinishManager(
            tripApi = api,
            pendingStore = persistentPendingStore,
            deliveryStatsStore = persistentDeliveryStats,
            finishRetryScheduler = scheduler,
        )

        val recovery = FakeTripRecoveryReplayCoordinator(
            outbox = outbox,
            deliveryStats = persistentDeliveryStats,
            finishManager = afterRestartManager,
            finishCallsProvider = { api.finishCalls },
        )

        recovery.replayPendingWork()
        recovery.replayPendingWork()

        assertEquals(1, outbox.deliverCalls)
        assertEquals(1, persistentDeliveryStats.getDeliveredBatches("session-1"))
        assertEquals(1, api.finishCalls)
        assertFalse(persistentPendingStore.exists("session-1"))

        assertEquals(
            listOf(
                "batch:session-1:batch-1",
                "finish:session-1",
            ),
            recovery.events,
        )
    }

    @Test
    fun pending_batches_survive_restart_and_recovery_dedups_delivery_across_second_restart() = runBlocking {
        val api = FakeTripApi()
        val persistentPendingStore = FakePendingStore()
        val persistentDeliveryStats = FakeDeliveryStats()
        val scheduler = FakeFinishRetryScheduler()
        val persistedOutboxDisk = linkedMapOf<String, FakePendingBatch>()

        val beforeRestartOutbox = FakePendingBatchOutbox(persistedOutboxDisk)

        beforeRestartOutbox.enqueue(
            sessionId = "session-1",
            batchId = "batch-1",
            batchSeq = 1,
        )
        beforeRestartOutbox.enqueue(
            sessionId = "session-1",
            batchId = "batch-2",
            batchSeq = 2,
        )

        persistentDeliveryStats.setDelivered("session-1", 0)

        val beforeRestartManager = TripFinishManager(
            tripApi = api,
            pendingStore = persistentPendingStore,
            deliveryStatsStore = persistentDeliveryStats,
            finishRetryScheduler = scheduler,
        )

        val queued = beforeRestartManager.finishTrip(finishCommand())

        assertTrue(queued is TripFinishResult.Queued)
        assertTrue(persistentPendingStore.exists("session-1"))
        assertEquals(0, api.finishCalls)

        val afterRestartOutbox = FakePendingBatchOutbox(persistedOutboxDisk)

        assertEquals(
            listOf("batch-1", "batch-2"),
            afterRestartOutbox.pendingOrdered().map { it.batchId },
        )

        val afterRestartManager = TripFinishManager(
            tripApi = api,
            pendingStore = persistentPendingStore,
            deliveryStatsStore = persistentDeliveryStats,
            finishRetryScheduler = scheduler,
        )

        val firstRecovery = FakeTripRecoveryReplayCoordinator(
            outbox = afterRestartOutbox,
            deliveryStats = persistentDeliveryStats,
            finishManager = afterRestartManager,
            finishCallsProvider = { api.finishCalls },
        )

        firstRecovery.replayPendingWork()

        assertEquals(2, afterRestartOutbox.deliverCalls)
        assertEquals(2, persistentDeliveryStats.getDeliveredBatches("session-1"))
        assertEquals(1, api.finishCalls)
        assertFalse(persistentPendingStore.exists("session-1"))
        assertEquals(
            listOf(
                "batch:session-1:batch-1",
                "batch:session-1:batch-2",
                "finish:session-1",
            ),
            firstRecovery.events,
        )

        val secondRestartOutbox = FakePendingBatchOutbox(persistedOutboxDisk)
        val secondRestartManager = TripFinishManager(
            tripApi = api,
            pendingStore = persistentPendingStore,
            deliveryStatsStore = persistentDeliveryStats,
            finishRetryScheduler = scheduler,
        )

        val secondRecovery = FakeTripRecoveryReplayCoordinator(
            outbox = secondRestartOutbox,
            deliveryStats = persistentDeliveryStats,
            finishManager = secondRestartManager,
            finishCallsProvider = { api.finishCalls },
        )

        secondRecovery.replayPendingWork()

        assertTrue(secondRestartOutbox.pendingOrdered().isEmpty())
        assertEquals(0, secondRestartOutbox.deliverCalls)
        assertEquals(2, persistentDeliveryStats.getDeliveredBatches("session-1"))
        assertEquals(1, api.finishCalls)
        assertTrue(secondRecovery.events.isEmpty())
    }

    private fun finishCommand(): FinishCommand {
        return FinishCommand(
            sessionId = "session-1",
            driverId = "driver-1",
            deviceId = "device-1",
            clientEndedAt = "2026-04-30T12:10:00Z",
            trackingMode = "single_trip",
            transportMode = "car",
            tripDurationSec = 600.0,
            finishReason = "manual_stop",
            clientMetrics = metrics(),
            deviceContext = buildJsonObject {
                put("app_state", "foreground")
            },
            tailActivityContext = buildJsonObject {
                put("dominant", "automotive")
            },
        )
    }

    private fun pendingFinish(
        queuedBecauseNoDeliveredBatches: Boolean,
    ): PendingTripFinishDto {
        return PendingTripFinishDto(
            sessionId = "session-1",
            driverId = "driver-1",
            deviceId = "device-1",
            clientEndedAt = "2026-04-30T12:10:00Z",
            createdAt = "2026-04-30T12:10:01Z",
            tripCore = com.alex.android_telemetry.telemetry.trips.api.TripCoreDto(
                tripId = "session-1",
                sessionId = "session-1",
                clientEndedAt = "2026-04-30T12:10:00Z",
            ),
            deviceMeta = com.alex.android_telemetry.telemetry.trips.api.DeviceMetaDto(
                platform = "Android",
                appVersion = "1.0",
                appBuild = "1",
                iosVersion = "Android 15",
                deviceModel = "Pixel 8",
                locale = "ru-RU",
                timezone = "Europe/Moscow",
            ),
            trackingMode = "single_trip",
            transportMode = "car",
            tripDurationSec = 600.0,
            finishReason = "manual_stop",
            clientMetrics = metrics(),
            tripMetricsRaw = null,
            tripSummary = null,
            deviceContext = buildJsonObject {
                put("app_state", "foreground")
            },
            tailActivityContext = buildJsonObject {
                put("dominant", "automotive")
            },
            queuedBecauseNoDeliveredBatches = queuedBecauseNoDeliveredBatches,
        )
    }

    private fun metrics(): ClientTripMetricsDto {
        val empty = ClientAggDto(
            count = 0,
            sumIntensity = 0.0,
            maxIntensity = 0.0,
            countPerKm = 0.0,
            sumPerKm = 0.0,
        )

        return ClientTripMetricsDto(
            tripDistanceM = 1000.0,
            tripDistanceKmFromGps = 1.0,
            brake = empty,
            accel = empty,
            road = empty,
            turn = empty,
        )
    }

    private data class FakePendingBatch(
        val sessionId: String,
        val batchId: String,
        val batchSeq: Int,
        val delivered: Boolean = false,
    )

    private class FakePendingBatchOutbox(
        private val persistedBatches: LinkedHashMap<String, FakePendingBatch> = linkedMapOf(),
    ) {
        var deliverCalls: Int = 0
            private set

        fun enqueue(
            sessionId: String,
            batchId: String,
            batchSeq: Int = 1,
        ) {
            persistedBatches[batchId] = FakePendingBatch(
                sessionId = sessionId,
                batchId = batchId,
                batchSeq = batchSeq,
            )
        }

        fun pendingOrdered(): List<FakePendingBatch> {
            return persistedBatches.values
                .filterNot { it.delivered }
                .sortedWith(
                    compareBy<FakePendingBatch> { it.batchSeq }
                        .thenBy { it.batchId }
                )
        }

        fun markDelivered(batchId: String) {
            val current = persistedBatches[batchId] ?: return

            if (!current.delivered) {
                deliverCalls += 1
                persistedBatches[batchId] = current.copy(delivered = true)
            }
        }
    }

    private class FakeTripRecoveryReplayCoordinator(
        private val outbox: FakePendingBatchOutbox,
        private val deliveryStats: FakeDeliveryStats,
        private val finishManager: TripFinishManager,
        private val finishCallsProvider: () -> Int,
    ) {
        val events = mutableListOf<String>()

        suspend fun replayPendingWork() {
            for (batch in outbox.pendingOrdered()) {
                outbox.markDelivered(batch.batchId)
                deliveryStats.incrementDelivered(batch.sessionId)
                events += "batch:${batch.sessionId}:${batch.batchId}"
            }

            val finishCallsBefore = finishCallsProvider()

            finishManager.retryPendingFinishes()

            val finishCallsAfter = finishCallsProvider()

            if (finishCallsAfter > finishCallsBefore) {
                events += "finish:session-1"
            }
        }
    }

    private class FakeTripApi : TripApi {
        var finishCalls: Int = 0
        var lastPending: PendingTripFinishDto? = null
        var failWith: Throwable? = null

        override suspend fun performFinishTrip(
            pending: PendingTripFinishDto,
        ): TripReportDto {
            finishCalls += 1
            lastPending = pending

            failWith?.let { throw it }

            return TripReportDto(
                sessionId = pending.sessionId,
                driverId = pending.driverId,
                deviceId = pending.deviceId,
                clientEndedAt = pending.clientEndedAt,
                batchesCount = 1,
                samplesCount = 10,
                eventsCount = 0,
                tripScore = 100.0,
                worstBatchScore = 100.0,
            )
        }

        override suspend fun fetchRecentTrips(
            deviceId: String,
            driverId: String,
            limit: Int,
        ): List<TripSummaryDto> = emptyList()

        override suspend fun fetchDriverHome(
            deviceId: String,
            driverId: String?,
        ): DriverHomeResponseDto {
            error("not used")
        }

        override suspend fun fetchTripReport(
            deviceId: String,
            sessionId: String,
            driverId: String,
        ): TripReportDto {
            error("not used")
        }
    }

    private class FakePendingStore : PendingTripFinishGateway {
        private val items = linkedMapOf<String, PendingTripFinishDto>()

        override fun getAll(): List<PendingTripFinishDto> {
            return items.values.toList()
        }

        override fun getBySessionId(sessionId: String): PendingTripFinishDto? {
            return items[sessionId]
        }

        override fun upsert(item: PendingTripFinishDto) {
            items[item.sessionId] = item
        }

        override fun markAttempt(
            sessionId: String,
            attemptedAt: String,
            errorMessage: String?,
        ) {
            val item = items[sessionId] ?: return
            items[sessionId] = item.copy(
                retryCount = item.retryCount + 1,
                lastAttemptAt = attemptedAt,
                lastError = errorMessage,
            )
        }

        override fun remove(sessionId: String) {
            items.remove(sessionId)
        }

        override fun exists(sessionId: String): Boolean {
            return items.containsKey(sessionId)
        }
    }

    private class FakeDeliveryStats : TripDeliveryStatsReader {
        private val deliveredBySession = mutableMapOf<String, Int>()

        fun setDelivered(sessionId: String, delivered: Int) {
            deliveredBySession[sessionId] = delivered
        }

        fun incrementDelivered(sessionId: String) {
            deliveredBySession[sessionId] = getDeliveredBatches(sessionId) + 1
        }

        override fun getDeliveredBatches(sessionId: String): Int {
            return deliveredBySession[sessionId] ?: 0
        }
    }

    private class FakeFinishRetryScheduler : FinishRetryGateway {
        var scheduleCalls: Int = 0

        override fun scheduleImmediate() {
            scheduleCalls += 1
        }
    }
}