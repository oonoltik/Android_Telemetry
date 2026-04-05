package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.TripStatus
import com.alex.android_telemetry.telemetry.model.RuntimeCounters
import com.alex.android_telemetry.telemetry.model.RuntimeRouteStats
import com.alex.android_telemetry.telemetry.session.TripSession

import com.alex.android_telemetry.telemetry.trips.api.TripReportDto

enum class TripFinishUiState {
    IDLE,
    FINISHING_IN_PROGRESS,
    FINISH_QUEUED,
    FINISHED_WITH_REPORT,
    FINISH_FAILED
}
data class TripRuntimeState(
    val status: TripStatus = TripStatus.Idle,
    val activeSession: TripSession? = null,
    val counters: RuntimeCounters = RuntimeCounters(),
    val routeStats: RuntimeRouteStats = RuntimeRouteStats(),
    val finishPending: Boolean = false,
    val lastError: String? = null,
    val lastReportSessionId: String? = null,
    val lastEndedAt: String? = null,
    val isTripActive: Boolean = false,
    val sessionId: String? = null,
    val driverId: String? = null,

    val finishUiState: TripFinishUiState = TripFinishUiState.IDLE,
    val lastTripReport: TripReportDto? = null,
    val lastFinishError: String? = null,

    val liveSamplesCount: Int = 0,
    val liveEventsCount: Int = 0,
    val liveDistanceMeters: Double = 0.0,
    val elapsedSec: Long = 0L
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
