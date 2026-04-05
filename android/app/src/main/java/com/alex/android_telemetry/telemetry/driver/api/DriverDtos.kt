package com.alex.android_telemetry.telemetry.driver.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverPrepareRequestDto(
    @SerialName("driver_id")
    val driverId: String,
)

@Serializable
data class DriverPrepareResponseDto(
    val status: String,
)

@Serializable
data class DriverRegisterRequestDto(
    @SerialName("driver_id")
    val driverId: String,
    val password: String,
)

@Serializable
data class DriverRegisterResponseDto(
    val status: String,
)

@Serializable
data class DriverLoginRequestDto(
    @SerialName("driver_id")
    val driverId: String,
    val password: String,
)

@Serializable
data class DriverLoginResponseDto(
    val status: String,
)

@Serializable
data class AccountDeleteRequestDto(
    @SerialName("driver_id")
    val driverId: String,
)

@Serializable
data class AccountDeleteResponseDto(
    val status: String,
)