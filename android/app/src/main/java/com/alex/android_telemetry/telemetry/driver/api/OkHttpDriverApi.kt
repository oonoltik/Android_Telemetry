package com.alex.android_telemetry.telemetry.driver.api

import android.util.Log
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpDriverApi(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val authManager: TelemetryAuthManager,
    private val euBaseUrl: String,
    private val ruBaseUrl: String,
) : DriverApi {

    override suspend fun prepareDriver(
        deviceId: String,
        driverId: String,
    ): DriverPrepareResponseDto {
        val token = authManager.getValidToken()
        val payload = DriverPrepareRequestDto(driverId = driverId)

        return try {
            performPrepare(
                baseUrl = euBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t

            Log.w(
                "DriverPrepare",
                "prepareDriver(): EU failed, trying RU fallback driverId=$driverId error=${t.message}"
            )

            performPrepare(
                baseUrl = ruBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        }
    }

    override suspend fun registerDriver(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverRegisterResponseDto {
        val token = authManager.getValidToken()
        val payload = DriverRegisterRequestDto(
            driverId = driverId,
            password = password,
        )

        return try {
            performRegister(
                baseUrl = euBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t

            Log.w(
                "DriverRegister",
                "registerDriver(): EU failed, trying RU fallback driverId=$driverId error=${t.message}"
            )

            performRegister(
                baseUrl = ruBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        }
    }

    override suspend fun loginDriver(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverLoginResponseDto {
        val token = authManager.getValidToken()
        val payload = DriverLoginRequestDto(
            driverId = driverId,
            password = password,
        )

        return try {
            performLogin(
                baseUrl = euBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t

            Log.w(
                "DriverLogin",
                "loginDriver(): EU failed, trying RU fallback driverId=$driverId error=${t.message}"
            )

            performLogin(
                baseUrl = ruBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        }
    }

    override suspend fun deleteAccount(
        deviceId: String,
        driverId: String,
    ): AccountDeleteResponseDto {
        val token = authManager.getValidToken()
        val payload = AccountDeleteRequestDto(driverId = driverId)

        return try {
            performDelete(
                baseUrl = euBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t

            Log.w(
                "AccountDelete",
                "deleteAccount(): EU failed, trying RU fallback driverId=$driverId error=${t.message}"
            )

            performDelete(
                baseUrl = ruBaseUrl,
                bearerToken = token,
                payload = payload,
            )
        }
    }

    private fun performPrepare(
        baseUrl: String,
        bearerToken: String,
        payload: DriverPrepareRequestDto,
    ): DriverPrepareResponseDto {
        val requestJson = json.encodeToString(DriverPrepareRequestDto.serializer(), payload)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/driver/prepare")
            .header("Authorization", "Bearer $bearerToken")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()

            Log.d(
                "DriverPrepare",
                "performPrepare(): route=${request.url} httpCode=$code body=$body"
            )

            if (!response.isSuccessful) {
                if (isRetryableHttp(code)) {
                    throw IOException("prepareDriver retryable httpCode=$code")
                }
                throw RuntimeException("prepareDriver failed httpCode=$code body=$body")
            }

            return json.decodeFromString(
                DriverPrepareResponseDto.serializer(),
                body
            )
        }
    }

    private fun performRegister(
        baseUrl: String,
        bearerToken: String,
        payload: DriverRegisterRequestDto,
    ): DriverRegisterResponseDto {
        val requestJson = json.encodeToString(DriverRegisterRequestDto.serializer(), payload)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/driver/register")
            .header("Authorization", "Bearer $bearerToken")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()

            Log.d(
                "DriverRegister",
                "performRegister(): route=${request.url} httpCode=$code body=$body"
            )

            if (!response.isSuccessful) {
                if (isRetryableHttp(code)) {
                    throw IOException("registerDriver retryable httpCode=$code")
                }
                throw RuntimeException("registerDriver failed httpCode=$code body=$body")
            }

            return json.decodeFromString(
                DriverRegisterResponseDto.serializer(),
                body
            )
        }
    }

    private fun performLogin(
        baseUrl: String,
        bearerToken: String,
        payload: DriverLoginRequestDto,
    ): DriverLoginResponseDto {
        val requestJson = json.encodeToString(DriverLoginRequestDto.serializer(), payload)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/driver/login")
            .header("Authorization", "Bearer $bearerToken")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()

            Log.d(
                "DriverLogin",
                "performLogin(): route=${request.url} httpCode=$code body=$body"
            )

            if (!response.isSuccessful) {
                if (isRetryableHttp(code)) {
                    throw IOException("loginDriver retryable httpCode=$code")
                }
                throw RuntimeException("loginDriver failed httpCode=$code body=$body")
            }

            return json.decodeFromString(
                DriverLoginResponseDto.serializer(),
                body
            )
        }
    }

    private fun performDelete(
        baseUrl: String,
        bearerToken: String,
        payload: AccountDeleteRequestDto,
    ): AccountDeleteResponseDto {
        val requestJson = json.encodeToString(AccountDeleteRequestDto.serializer(), payload)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/account/delete")
            .header("Authorization", "Bearer $bearerToken")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()

            Log.d(
                "AccountDelete",
                "performDelete(): route=${request.url} httpCode=$code body=$body"
            )

            if (!response.isSuccessful) {
                if (isRetryableHttp(code)) {
                    throw IOException("deleteAccount retryable httpCode=$code")
                }
                throw RuntimeException("deleteAccount failed httpCode=$code body=$body")
            }

            return json.decodeFromString(
                AccountDeleteResponseDto.serializer(),
                body
            )
        }
    }

    private fun isRetryable(t: Throwable): Boolean {
        return t is IOException
    }

    private fun isRetryableHttp(code: Int): Boolean {
        return code == 408 || code == 429 || code in 500..599
    }
}