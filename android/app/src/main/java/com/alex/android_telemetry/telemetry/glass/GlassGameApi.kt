package com.alex.android_telemetry.telemetry.glass

import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import com.alex.android_telemetry.telemetry.delivery.TelemetryBackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GlassGameApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val authManager: TelemetryAuthManager,
) {
    suspend fun upload(batch: GlassGameBatchDto) {
        val token = authManager.getValidToken()
        val payload = json.encodeToString(batch)

        val euError = runCatching {
            post(
                baseUrl = TelemetryBackendConfig.EU_BASE_URL,
                token = token,
                payload = payload,
            )
        }.exceptionOrNull()

        if (euError == null) return

        post(
            baseUrl = TelemetryBackendConfig.RU_BASE_URL,
            token = token,
            payload = payload,
        )
    }

    private suspend fun post(
        baseUrl: String,
        token: String,
        payload: String,
    ) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/glass_game_ingest")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "glass_game_ingest failed code=${response.code} body=${response.body?.string().orEmpty()}"
                    )
                }
            }
        }
    }
}