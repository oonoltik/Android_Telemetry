package com.alex.android_telemetry.telemetry.integration

import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft
import com.alex.android_telemetry.telemetry.model.TelemetryBatchDraft

class CurrentPipelineBridge(
    private val deliveryFacade: DeliveryFacade,
    private val finishDispatchFacade: FinishDispatchFacade
) {
    suspend fun sendBatch(batch: TelemetryBatchDraft): DeliveryResult = deliveryFacade.enqueueOrSend(batch)
    suspend fun sendFinish(payload: FinishPayloadDraft): FinishDispatchOutcome = finishDispatchFacade.dispatchFinish(payload)
}
