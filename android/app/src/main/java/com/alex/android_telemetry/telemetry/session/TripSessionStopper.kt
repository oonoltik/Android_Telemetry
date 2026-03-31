package com.alex.android_telemetry.telemetry.session

import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeStore

class TripSessionStopper(
    private val clockProvider: ClockProvider,
    private val tripFinishCoordinator: TripFinishCoordinator,
    private val sessionRepository: TripSessionRepository,
    private val runtimeStore: TripRuntimeStore
) {
    suspend fun stop(finishReason: FinishReason): FinishPayloadDraft? {
        val session = runtimeStore.currentState().activeSession ?: return null
        val endedAt = clockProvider.nowIsoStringUtc()
        val durationSec = ((clockProvider.nowEpochMillis() - session.startedAtEpochMillis).coerceAtLeast(0L) / 1000.0)

        val payload = FinishPayloadDraft(
            deviceId = session.deviceId,
            driverId = session.driverId,
            sessionId = session.sessionId,
            clientEndedAt = endedAt,
            trackingMode = session.trackingMode,
            transportMode = session.transportMode,
            tripDurationSec = durationSec,
            finishReason = finishReason
        )

        val result = tripFinishCoordinator.dispatchFinish(payload)
        runtimeStore.update {
            it.copy(
                finishPending = result.queued,
                lastReportSessionId = result.reportSessionId ?: session.sessionId,
                lastEndedAt = endedAt,
                lastError = result.error
            )
        }
        sessionRepository.clearActiveSession()
        return payload
    }
}
