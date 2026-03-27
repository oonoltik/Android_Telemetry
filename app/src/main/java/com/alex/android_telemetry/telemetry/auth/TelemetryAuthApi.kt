package com.alex.android_telemetry.telemetry.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TelemetryAuthApi(
    private val baseUrl: String,
    private val androidRegisterKey: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun requestChallenge(): AuthChallengeResponseDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/auth/challenge")
            .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                error("auth/challenge failed: code=${response.code} body=$body")
            }

            json.decodeFromString(AuthChallengeResponseDto.serializer(), body)
        }
    }

    suspend fun register(
        requestDto: AuthRegisterRequestDto,
    ): AuthRegisterResponseDto = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(AuthRegisterRequestDto.serializer(), requestDto)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/auth/register")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Android-Register-Key", androidRegisterKey)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                error("auth/register failed: code=${response.code} body=$body")
            }

            json.decodeFromString(AuthRegisterResponseDto.serializer(), body)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}