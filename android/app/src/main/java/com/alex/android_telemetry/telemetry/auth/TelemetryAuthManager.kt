package com.alex.android_telemetry.telemetry.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import android.util.Log

class TelemetryAuthManager(
    private val authApi: TelemetryAuthApi,
    private val tokenStore: TelemetryTokenStore,
    private val keyIdStore: TelemetryKeyIdStore,
    private val deviceIdProvider: TelemetryDeviceIdProvider,
) {
    private val mutex = Mutex()

    suspend fun getValidToken(): String {
        val cached = tokenStore.getToken()
        val expiresAtRaw = tokenStore.getExpiresAt()

        if (!cached.isNullOrBlank() && !isExpired(expiresAtRaw)) {
            Log.d("TelemetryDelivery", "auth: using cached token")
            return cached
        }

        return mutex.withLock {
            val cached2 = tokenStore.getToken()
            val expiresAtRaw2 = tokenStore.getExpiresAt()

            if (!cached2.isNullOrBlank() && !isExpired(expiresAtRaw2)) {
                return@withLock cached2
            }

            Log.d("TelemetryDelivery", "auth: token missing/expired, registering")
            val token = registerAndGetToken()
            tokenStore.save(token.token, token.expiresAt)
            token.token
        }
    }

    suspend fun invalidateToken() {
        mutex.withLock {
            tokenStore.clearToken()
        }
    }

    suspend fun clearAllAuthState() {
        mutex.withLock {
            tokenStore.clearAll()
            keyIdStore.clear()
        }
    }

    private suspend fun registerAndGetToken(): AuthRegisterResponseDto {
        val deviceId = deviceIdProvider.get()
        val keyId = keyIdStore.getOrCreate(deviceId)

        Log.d("TelemetryDelivery", "auth: request challenge")
        val challenge = authApi.requestChallenge()
        Log.d("TelemetryDelivery", "auth: challenge received id=${challenge.challengeId}")

        val request = AuthRegisterRequestDto(
            deviceId = deviceId,
            keyId = keyId,
            challengeId = challenge.challengeId,
            attestationObjectB64 = TelemetryAuthConfig.STUB_ATTESTATION_OBJECT_B64,
            platform = TelemetryAuthConfig.PLATFORM,
            appPackage = TelemetryAuthConfig.APP_PACKAGE,
        )

        Log.d("TelemetryDelivery", "auth: register deviceId=$deviceId keyId=$keyId")
        val response = authApi.register(request)
        Log.d("TelemetryDelivery", "auth: register success")

        return response
    }

    private fun isExpired(expiresAtRaw: String?): Boolean {
        if (expiresAtRaw.isNullOrBlank()) return true

        return try {
            val expiresAt = Instant.parse(expiresAtRaw)
            Clock.System.now() >= (expiresAt - 5.minutes)
        } catch (_: Throwable) {
            true
        }
    }
}