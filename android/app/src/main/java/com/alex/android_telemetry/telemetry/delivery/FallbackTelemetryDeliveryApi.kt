package com.alex.android_telemetry.telemetry.delivery

import android.util.Log
import com.alex.android_telemetry.telemetry.delivery.api.TelemetryApiResult
import com.alex.android_telemetry.telemetry.delivery.api.TelemetryDeliveryApi

class FallbackTelemetryDeliveryApi(
    private val primary: TelemetryDeliveryApi,
    private val fallback: TelemetryDeliveryApi,
) : TelemetryDeliveryApi {

    override suspend fun sendBatch(payloadJson: String): TelemetryApiResult {
        val primaryResult = primary.sendBatch(payloadJson)

        return when (primaryResult) {
            is TelemetryApiResult.Success -> primaryResult
            is TelemetryApiResult.HttpError -> primaryResult
            is TelemetryApiResult.NetworkError -> {
                Log.d("TelemetryDelivery", "Primary delivery failed, trying RU fallback")
                fallback.sendBatch(payloadJson)
            }
        }
    }
}