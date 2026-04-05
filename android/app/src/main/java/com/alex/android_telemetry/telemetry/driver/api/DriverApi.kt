package com.alex.android_telemetry.telemetry.driver.api

interface DriverApi {
    suspend fun prepareDriver(
        deviceId: String,
        driverId: String,
    ): DriverPrepareResponseDto

    suspend fun registerDriver(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverRegisterResponseDto

    suspend fun loginDriver(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverLoginResponseDto

    suspend fun deleteAccount(
        deviceId: String,
        driverId: String,
    ): AccountDeleteResponseDto
}