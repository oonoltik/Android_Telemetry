package com.alex.android_telemetry.telemetry.session

import com.alex.android_telemetry.telemetry.integration.FinishDispatchFacade
import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft

class TripFinishCoordinator(
    private val finishDispatchFacade: FinishDispatchFacade
) {
    suspend fun dispatchFinish(payload: FinishPayloadDraft): FinishDispatchResult {
        val outcome = finishDispatchFacade.dispatchFinish(payload)
        return FinishDispatchResult(
            accepted = outcome.accepted,
            queued = outcome.queued,
            reportSessionId = outcome.reportSessionId,
            error = outcome.error
        )
    }
}

data class FinishDispatchResult(
    val accepted: Boolean,
    val queued: Boolean,
    val reportSessionId: String?,
    val error: String?
)
