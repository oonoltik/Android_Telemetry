package com.alex.android_telemetry.telemetry.integration

import com.alex.android_telemetry.telemetry.domain.TripFinishResult
import com.alex.android_telemetry.telemetry.domain.TripRepository
import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft
import com.alex.android_telemetry.telemetry.trips.api.FinishCommand

class RuntimeFinishDispatchFacade(
    private val tripRepository: TripRepository,
) : FinishDispatchFacade {

    override suspend fun dispatchFinish(payload: FinishPayloadDraft): FinishDispatchOutcome {
        return try {
            val command = FinishCommand(
                sessionId = payload.sessionId,
                driverId = payload.driverId.orEmpty(),
                deviceId = payload.deviceId,
                clientEndedAt = payload.clientEndedAt,
                trackingMode = payload.trackingMode,
                transportMode = payload.transportMode,
                tripDurationSec = payload.tripDurationSec,
                finishReason = payload.finishReason,
                clientMetrics = payload.clientMetrics,
                tripSummary = payload.tripSummary,
                tripMetricsRaw = payload.tripMetricsRaw,
                deviceContext = payload.deviceContext,
                tailActivityContext = payload.tailActivityContext,
            )

            when (val result = tripRepository.finishTrip(command)) {
                is TripFinishResult.Sent -> {
                    FinishDispatchOutcome(
                        accepted = true,
                        queued = false,
                        reportSessionId = result.report.sessionId,
                        error = null,
                    )
                }

                is TripFinishResult.Queued -> {
                    FinishDispatchOutcome(
                        accepted = true,
                        queued = true,
                        reportSessionId = result.placeholderReport?.sessionId ?: payload.sessionId,
                        error = result.reason,
                    )
                }

                is TripFinishResult.Failed -> {
                    FinishDispatchOutcome(
                        accepted = false,
                        queued = false,
                        reportSessionId = payload.sessionId,
                        error = result.message,
                    )
                }
            }
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