package com.alex.android_telemetry.telemetry.delivery.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpTelemetryDeliveryApi(
    private val baseUrl: String,
    private val route: DeliveryRoute,
    private val authTokenProvider: suspend () -> String?,
    private val onUnauthorized: suspend () -> Unit,
    private val client: OkHttpClient,
    private val json: Json,
) : TelemetryDeliveryApi {

    override suspend fun sendBatch(payloadJson: String): TelemetryApiResult =
        withContext(Dispatchers.IO) {
            val token = authTokenProvider()
            val body = payloadJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/ingest")
                .post(body)
                .apply {
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()

            Log.d(
                "TelemetryDelivery",
                "HTTP POST ${baseUrl.trimEnd('/')}/ingest route=$route tokenPresent=${!token.isNullOrBlank()} payload=${payloadJson.take(4000)}"
            )

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    Log.d(
                        "TelemetryDelivery",
                        "HTTP response code=${response.code} route=$route body=${responseBody?.take(300)}"
                    )

                    if (response.isSuccessful) {
                        val parsed = runCatching {
                            responseBody
                                ?.takeIf { it.isNotBlank() }
                                ?.let { json.decodeFromString<TelemetryIngestSuccessDto>(it) }
                        }.getOrNull()

                        return@withContext TelemetryApiResult.Success(
                            status = parsed?.status,
                            duplicate = parsed?.duplicate,
                            route = route,
                        )
                    }

                    if (response.code == 409) {
                        return@withContext TelemetryApiResult.Success(
                            status = "duplicate",
                            duplicate = true,
                            route = route,
                        )
                    }

                    if (response.code == 401 || response.code == 403) {
                        onUnauthorized()
                    }

                    return@withContext TelemetryApiResult.HttpError(
                        code = response.code,
                        body = responseBody,
                    )
                }
            } catch (t: Throwable) {
                Log.d(
                    "TelemetryDelivery",
                    "HTTP exception route=$route: ${t::class.java.simpleName}: ${t.message}"
                )

                return@withContext TelemetryApiResult.NetworkError(t.message)
            }
        }
}