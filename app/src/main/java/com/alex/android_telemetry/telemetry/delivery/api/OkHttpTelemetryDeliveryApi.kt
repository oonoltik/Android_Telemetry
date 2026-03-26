package com.alex.android_telemetry.telemetry.delivery.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpTelemetryDeliveryApi(
    private val baseUrl: String,
    private val authTokenProvider: suspend () -> String?,
    private val client: OkHttpClient,
    private val json: Json,
) : TelemetryDeliveryApi {

    override suspend fun sendBatch(payloadJson: String): TelemetryApiResult = withContext(Dispatchers.IO) {
        val token = authTokenProvider()
        val body = payloadJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/ingest")
            .post(body)
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    val parsed = runCatching {
                        responseBody
                            ?.takeIf { it.isNotBlank() }
                            ?.let { json.decodeFromString<TelemetryIngestSuccessDto>(it) }
                    }.getOrNull()

                    return@withContext TelemetryApiResult.Success(
                        status = parsed?.status,
                        duplicate = parsed?.duplicate,
                    )
                }

                if (response.code == 409) {
                    return@withContext TelemetryApiResult.Success(
                        status = "duplicate",
                        duplicate = true,
                    )
                }

                return@withContext TelemetryApiResult.HttpError(
                    code = response.code,
                    body = responseBody,
                )
            }
        } catch (t: Throwable) {
            TelemetryApiResult.NetworkError(t.message)
        }
    }
}