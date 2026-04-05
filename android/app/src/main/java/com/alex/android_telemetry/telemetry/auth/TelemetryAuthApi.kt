package com.alex.android_telemetry.telemetry.auth

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TelemetryAuthApi(
    private val euBaseUrl: String,
    private val ruBaseUrl: String,
    private val androidRegisterKey: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun requestChallenge(): AuthChallengeResponseDto = withContext(Dispatchers.IO) {
        try {
            requestChallengeOnce(euBaseUrl)
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t

            Log.w(
                "TelemetryDelivery",
                "auth/challenge EU failed, trying RU fallback: ${t.message}"
            )

            requestChallengeOnce(ruBaseUrl)
        }
    }

    suspend fun register(
        requestDto: AuthRegisterRequestDto,
    ): AuthRegisterResponseDto = withContext(Dispatchers.IO) {
        try {
            registerOnce(
                baseUrl = euBaseUrl,
                requestDto = requestDto,
            )
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t

            Log.w(
                "TelemetryDelivery",
                "auth/register EU failed, trying RU fallback: ${t.message}"
            )

            registerOnce(
                baseUrl = ruBaseUrl,
                requestDto = requestDto,
            )
        }
    }

    private fun requestChallengeOnce(baseUrl: String): AuthChallengeResponseDto {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/auth/challenge")
            .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d(
                "TelemetryDelivery",
                "auth/challenge route=${request.url} code=${response.code} body=$body"
            )

            if (!response.isSuccessful) {
                if (isRetryableHttp(response.code)) {
                    throw IOException("auth/challenge retryable code=${response.code} body=$body")
                }
                error("auth/challenge failed: code=${response.code} body=$body")
            }

            return json.decodeFromString(AuthChallengeResponseDto.serializer(), body)
        }
    }

    private fun registerOnce(
        baseUrl: String,
        requestDto: AuthRegisterRequestDto,
    ): AuthRegisterResponseDto {
        val payload = json.encodeToString(AuthRegisterRequestDto.serializer(), requestDto)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/auth/register")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Android-Register-Key", androidRegisterKey)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d(
                "TelemetryDelivery",
                "auth/register route=${request.url} code=${response.code} body=$body"
            )

            if (!response.isSuccessful) {
                if (isRetryableHttp(response.code)) {
                    throw IOException("auth/register retryable code=${response.code} body=$body")
                }
                error("auth/register failed: code=${response.code} body=$body")
            }

            return json.decodeFromString(AuthRegisterResponseDto.serializer(), body)
        }
    }

    private fun isRetryable(t: Throwable): Boolean {
        return t is IOException
    }

    private fun isRetryableHttp(code: Int): Boolean {
        return code == 408 || code == 429 || code in 500..599
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}