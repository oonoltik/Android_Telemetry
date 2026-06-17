package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState

data class TripRuntimeSnapshot(
    val telemetryMode: TelemetryMode = TelemetryMode.IDLE,
    val isTracking: Boolean = false,
    val sessionId: String? = null,
    val trackingMode: TrackingMode? = null,
    val startedAt: String? = null,
    val elapsedSec: Long = 0L,
    val samplesBuffered: Int = 0,
    val batchesCreated: Int = 0,
    val batchesDelivered: Int = 0,
    val euDelivered: Int = 0,
    val ruDelivered: Int = 0,
    val finishPending: Boolean = false,
    val lastError: String? = null,
    val lastReportSessionId: String? = null,
    val dayMonitoringEnabled: Boolean = false,
    val dayMonitoringAutoTripActive: Boolean = false,
)

fun TripRuntimeState.toSnapshot(
    nowEpochMillis: Long = System.currentTimeMillis(),
): TripRuntimeSnapshot {
    val elapsedSec = startedAt
        ?.let { ((nowEpochMillis - it.toEpochMilliseconds()).coerceAtLeast(0L)) / 1000L }
        ?: 0L

    return TripRuntimeSnapshot(
        telemetryMode = telemetryMode,
        isTracking = telemetryMode == TelemetryMode.COLLECTING || telemetryMode == TelemetryMode.PAUSED,
        sessionId = sessionId,
        trackingMode = trackingMode,
        startedAt = startedAt?.toString(),
        elapsedSec = elapsedSec,
        samplesBuffered = counters.samplesBuffered,
        batchesCreated = counters.batchesCreated,
        batchesDelivered = counters.batchesDelivered,
        euDelivered = routeStats.euDelivered,
        ruDelivered = routeStats.ruDelivered,
        finishPending = pendingFinish,
        lastError = lastFinishError,
        lastReportSessionId = lastTripReport?.sessionId,
        dayMonitoringEnabled = dayMonitoringEnabled,
        dayMonitoringAutoTripActive = dayMonitoringAutoTripActive,
    )
}