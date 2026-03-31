package com.alex.android_telemetry.telemetry.integration

import com.alex.android_telemetry.telemetry.model.TelemetryBatchDraft

interface DeliveryFacade {
    suspend fun enqueueOrSend(batch: TelemetryBatchDraft): DeliveryResult
}

data class DeliveryResult(
    val accepted: Boolean,
    val delivered: Boolean,
    val route: DeliveryRoute?,
    val error: String? = null
)

enum class DeliveryRoute { EU, RU }
