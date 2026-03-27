package com.alex.android_telemetry.telemetry.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthChallengeResponseDto(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("challenge_b64") val challengeB64: String,
    @SerialName("expires_in_sec") val expiresInSec: Int,
)

@Serializable
data class AuthRegisterRequestDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("attestation_object_b64") val attestationObjectB64: String,
    @SerialName("platform") val platform: String,
    @SerialName("app_package") val appPackage: String,
)

@Serializable
data class AuthRegisterResponseDto(
    @SerialName("token") val token: String,
    @SerialName("expires_at") val expiresAt: String,
)