package com.alex.android_telemetry.telemetry.integration

import com.alex.android_telemetry.telemetry.domain.TripRepository
import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft

class RuntimeFinishDispatchFacade(
    private val tripRepository: TripRepository,
) : FinishDispatchFacade {

    override suspend fun dispatchFinish(payload: FinishPayloadDraft): FinishDispatchOutcome {
        return try {
            val report = tripRepository.finishTrip(
                sessionId = payload.sessionId,
                driverId = payload.driverId.orEmpty(),
                deviceId = payload.deviceId,
                trackingMode = payload.trackingMode.name.lowercase(),
                transportMode = payload.transportMode.name.lowercase(),
                clientEndedAt = payload.clientEndedAt,
                tripDurationSec = payload.tripDurationSec,
                finishReason = payload.finishReason.name.lowercase(),
                clientMetrics = null,
                deviceContextJson = null,
                tailActivityContextJson = null,
            )

            FinishDispatchOutcome(
                accepted = true,
                queued = false,
                reportSessionId = report.sessionId,
                error = null,
            )
        } catch (t: Throwable) {
            FinishDispatchOutcome(
                accepted = false,
                queued = true,
                reportSessionId = payload.sessionId,
                error = t.message,
            )
        }
    }
}