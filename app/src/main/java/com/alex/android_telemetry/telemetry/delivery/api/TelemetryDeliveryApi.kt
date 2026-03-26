package com.alex.android_telemetry.telemetry.delivery.api

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryIngestSuccessDto(
    val status: String? = null,
    val duplicate: Boolean? = null,
)

sealed interface TelemetryApiResult {
    data class Success(
        val status: String?,
        val duplicate: Boolean?,
    ) : TelemetryApiResult

    data class HttpError(
        val code: Int,
        val body: String?,
    ) : TelemetryApiResult

    data class NetworkError(
        val message: String?,
    ) : TelemetryApiResult
}

interface TelemetryDeliveryApi {
    suspend fun sendBatch(payloadJson: String): TelemetryApiResult
}