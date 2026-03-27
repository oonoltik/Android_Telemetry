package com.alex.android_telemetry.telemetry.delivery

import android.content.Context
import com.alex.android_telemetry.telemetry.delivery.api.OkHttpTelemetryDeliveryApi
import com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class TelemetryDeliveryGraph(
    val processor: TelemetryDeliveryProcessor,
) {
    companion object {
        fun from(context: Context): TelemetryDeliveryGraph {
            val db = TelemetryDatabase.get(context)
            val dao = db.telemetryOutboxDao()

            val repository = TelemetryOutboxRepository(dao)

            val policy = TelemetryDeliveryPolicy()
            val retryDecider = TelemetryRetryDecider(policy)
            val backoff = TelemetryBackoffCalculator(policy)

            val okHttpClient = OkHttpClient()

            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

            val authTokenProvider: suspend () -> String? = { null }

            val euApi = OkHttpTelemetryDeliveryApi(
                baseUrl = TelemetryBackendConfig.EU_BASE_URL,
                authTokenProvider = authTokenProvider,
                client = okHttpClient,
                json = json,
            )

            val ruApi = OkHttpTelemetryDeliveryApi(
                baseUrl = TelemetryBackendConfig.RU_BASE_URL,
                authTokenProvider = authTokenProvider,
                client = okHttpClient,
                json = json,
            )

            val deliveryApi = FallbackTelemetryDeliveryApi(
                primary = euApi,
                fallback = ruApi,
            )

            val processor = TelemetryDeliveryProcessor(
                repository = repository,
                api = deliveryApi,
                retryDecider = retryDecider,
                backoffCalculator = backoff,
                policy = policy,
            )

            return TelemetryDeliveryGraph(processor)
        }
    }
}