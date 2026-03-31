package com.alex.android_telemetry.telemetry.integration

import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft

interface FinishDispatchFacade {
    suspend fun dispatchFinish(payload: FinishPayloadDraft): FinishDispatchOutcome
}

data class FinishDispatchOutcome(
    val accepted: Boolean,
    val queued: Boolean,
    val reportSessionId: String?,
    val error: String? = null
)
