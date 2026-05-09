package com.alex.android_telemetry.telemetry.delivery

import com.alex.android_telemetry.telemetry.delivery.api.DeliveryRoute
import com.alex.android_telemetry.telemetry.trips.api.PendingTripFinishDto
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryGateway
import com.alex.android_telemetry.telemetry.trips.storage.PendingTripFinishGateway
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryFinishRetryHookTest {

    @Test
    fun delivered_batch_without_pending_finish_does_not_schedule_retry() {
        val pending = FakePendingStore()
        val stats = FakeStatsStore()
        val scheduler = FakeScheduler()

        val hook = DeliveryFinishRetryHook(
            pendingStore = pending,
            statsStore = stats,
            retryScheduler = scheduler,
        )

        hook.onBatchDelivered("session-1", DeliveryRoute.EU)

        assertEquals(1, stats.getDeliveredBatches("session-1"))
        assertEquals(0, scheduler.scheduleCalls)
    }

    @Test
    fun first_delivered_batch_with_pending_finish_schedules_once() {
        val pending = FakePendingStore()
        val stats = FakeStatsStore()
        val scheduler = FakeScheduler()

        pending.add("session-1")

        val hook = DeliveryFinishRetryHook(
            pendingStore = pending,
            statsStore = stats,
            retryScheduler = scheduler,
        )

        hook.onBatchDelivered("session-1", DeliveryRoute.EU)

        assertEquals(1, stats.getDeliveredBatches("session-1"))
        assertEquals(1, scheduler.scheduleCalls)
    }

    @Test
    fun second_delivered_batch_with_pending_finish_does_not_schedule_again() {
        val pending = FakePendingStore()
        val stats = FakeStatsStore()
        val scheduler = FakeScheduler()

        pending.add("session-1")

        val hook = DeliveryFinishRetryHook(
            pendingStore = pending,
            statsStore = stats,
            retryScheduler = scheduler,
        )

        hook.onBatchDelivered("session-1", DeliveryRoute.EU)
        hook.onBatchDelivered("session-1", DeliveryRoute.RU)

        assertEquals(2, stats.getDeliveredBatches("session-1"))
        assertEquals(1, scheduler.scheduleCalls)
    }

    @Test
    fun already_delivered_session_with_pending_finish_does_not_schedule() {
        val pending = FakePendingStore()
        val stats = FakeStatsStore()
        val scheduler = FakeScheduler()

        pending.add("session-1")
        stats.recordDeliveredBatch("session-1", DeliveryRoute.EU)

        val hook = DeliveryFinishRetryHook(
            pendingStore = pending,
            statsStore = stats,
            retryScheduler = scheduler,
        )

        hook.onBatchDelivered("session-1", DeliveryRoute.EU)

        assertEquals(2, stats.getDeliveredBatches("session-1"))
        assertEquals(0, scheduler.scheduleCalls)
    }

    @Test
    fun duplicate_first_delivery_callbacks_are_deduped_per_session() {
        val pending = FakePendingStore()
        val stats = FakeStatsStore()
        val scheduler = FakeScheduler()

        pending.add("session-1")

        val hook = DeliveryFinishRetryHook(
            pendingStore = pending,
            statsStore = stats,
            retryScheduler = scheduler,
        )

        hook.onBatchDelivered("session-1", DeliveryRoute.EU)
        hook.onBatchDelivered("session-1", DeliveryRoute.EU)
        hook.onBatchDelivered("session-1", DeliveryRoute.EU)

        assertEquals(3, stats.getDeliveredBatches("session-1"))
        assertEquals(1, scheduler.scheduleCalls)
    }

    private class FakePendingStore : PendingTripFinishGateway {
        private val pendingSessionIds = mutableSetOf<String>()

        fun add(sessionId: String) {
            pendingSessionIds.add(sessionId)
        }

        override fun getAll(): List<PendingTripFinishDto> = emptyList()

        override fun getBySessionId(sessionId: String): PendingTripFinishDto? = null

        override fun upsert(item: PendingTripFinishDto) {
            pendingSessionIds.add(item.sessionId)
        }

        override fun markAttempt(
            sessionId: String,
            attemptedAt: String,
            errorMessage: String?,
        ) = Unit

        override fun remove(sessionId: String) {
            pendingSessionIds.remove(sessionId)
        }

        override fun exists(sessionId: String): Boolean {
            return pendingSessionIds.contains(sessionId)
        }
    }

    private class FakeStatsStore : DeliveryFinishRetryStatsStore {
        private val delivered = mutableMapOf<String, Int>()

        override fun getDeliveredBatches(sessionId: String): Int {
            return delivered[sessionId] ?: 0
        }

        override fun recordDeliveredBatch(
            sessionId: String,
            route: DeliveryRoute,
        ): Int {
            val next = getDeliveredBatches(sessionId) + 1
            delivered[sessionId] = next
            return next
        }
    }

    private class FakeScheduler : FinishRetryGateway {
        var scheduleCalls: Int = 0

        override fun scheduleImmediate() {
            scheduleCalls += 1
        }
    }
}