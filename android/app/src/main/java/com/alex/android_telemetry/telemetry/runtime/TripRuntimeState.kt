package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.TripStatus
import com.alex.android_telemetry.telemetry.model.RuntimeCounters
import com.alex.android_telemetry.telemetry.model.RuntimeRouteStats
import com.alex.android_telemetry.telemetry.session.TripSession

data class TripRuntimeState(
    val status: TripStatus = TripStatus.Idle,
    val activeSession: TripSession? = null,
    val counters: RuntimeCounters = RuntimeCounters(),
    val routeStats: RuntimeRouteStats = RuntimeRouteStats(),
    val finishPending: Boolean = false,
    val lastError: String? = null,
    val lastReportSessionId: String? = null,
    val lastEndedAt: String? = null
) {
    fun toSnapshot(): TripRuntimeSnapshot = TripRuntimeSnapshot(
        status = status,
        isTracking = activeSession != null && status != TripStatus.Finished && status != TripStatus.Idle,
        sessionId = activeSession?.sessionId,
        driverId = activeSession?.driverId,
        trackingMode = activeSession?.trackingMode,
        transportMode = activeSession?.transportMode,
        startedAt = activeSession?.startedAt,
        endedAt = lastEndedAt,
        elapsedSec = counters.elapsedSec,
        samplesBuffered = counters.samplesBuffered,
        batchesCreated = counters.batchesCreated,
        batchesDelivered = counters.batchesDelivered,
        euDelivered = routeStats.euDelivered,
        ruDelivered = routeStats.ruDelivered,
        finishPending = finishPending,
        lastError = lastError,
        lastReportSessionId = lastReportSessionId
    )
}
