package com.alex.android_telemetry.telemetry.delivery


import android.content.Context
import com.alex.android_telemetry.telemetry.delivery.api.OkHttpTelemetryDeliveryApi
import com.alex.android_telemetry.telemetry.delivery.api.TelemetryDeliveryApi
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

            val api: TelemetryDeliveryApi = OkHttpTelemetryDeliveryApi(
                baseUrl = "https://your.backend.com", // TODO заменить
                authTokenProvider = { null }, // TODO подключить auth
                client = OkHttpClient(),
                json = Json {
                    ignoreUnknownKeys = true
                }
            )

            val processor = TelemetryDeliveryProcessor(
                repository = repository,
                api = api,
                retryDecider = retryDecider,
                backoffCalculator = backoff,
                policy = policy,
            )

            return TelemetryDeliveryGraph(processor)
        }
    }
}