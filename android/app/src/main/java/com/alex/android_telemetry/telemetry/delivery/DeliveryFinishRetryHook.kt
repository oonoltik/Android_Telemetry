package com.alex.android_telemetry.telemetry.delivery

import android.util.Log
import com.alex.android_telemetry.telemetry.delivery.api.DeliveryRoute
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryGateway
import com.alex.android_telemetry.telemetry.trips.storage.PendingTripFinishGateway

interface DeliveryFinishRetryStatsStore {
    fun getDeliveredBatches(sessionId: String): Int

    fun recordDeliveredBatch(
        sessionId: String,
        route: DeliveryRoute,
    ): Int
}

class DeliveryFinishRetryHook(
    private val pendingStore: PendingTripFinishGateway,
    private val statsStore: DeliveryFinishRetryStatsStore,
    private val retryScheduler: FinishRetryGateway,
) {
    private val scheduledSessions = mutableSetOf<String>()

    fun onBatchDelivered(
        sessionId: String,
        route: DeliveryRoute,
    ) {
        val hadPendingFinish = pendingStore.exists(sessionId)
        val deliveredBefore = statsStore.getDeliveredBatches(sessionId)
        val deliveredAfter = statsStore.recordDeliveredBatch(sessionId, route)

        Log.d(
            "TelemetryTrip",
            "DeliveryFinishRetryHook.onBatchDelivered(): sessionId=$sessionId route=$route hadPendingFinish=$hadPendingFinish deliveredBefore=$deliveredBefore deliveredAfter=$deliveredAfter"
        )

        if (!hadPendingFinish) {
            return
        }

        val isFirstDeliveredBatch = deliveredBefore == 0 && deliveredAfter > 0
        if (!isFirstDeliveredBatch) {
            return
        }

        val shouldSchedule = scheduledSessions.add(sessionId)
        if (!shouldSchedule) {
            Log.d(
                "TelemetryTrip",
                "DeliveryFinishRetryHook.onBatchDelivered(): dedup skip sessionId=$sessionId"
            )
            return
        }

        Log.d(
            "TelemetryTrip",
            "DeliveryFinishRetryHook.onBatchDelivered(): first delivered batch with pending finish sessionId=$sessionId -> schedule retry"
        )

        retryScheduler.scheduleImmediate()
    }
}