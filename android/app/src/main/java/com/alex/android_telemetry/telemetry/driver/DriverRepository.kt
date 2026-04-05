package com.alex.android_telemetry.telemetry.driver

import com.alex.android_telemetry.telemetry.driver.api.AccountDeleteResponseDto
import com.alex.android_telemetry.telemetry.driver.api.DriverApi
import com.alex.android_telemetry.telemetry.driver.api.DriverLoginResponseDto
import com.alex.android_telemetry.telemetry.driver.api.DriverPrepareResponseDto
import com.alex.android_telemetry.telemetry.driver.api.DriverRegisterResponseDto

class DriverRepository(
    private val driverApi: DriverApi,
    private val driverIdStore: DriverIdStore,
) {
    fun getCurrentDriverId(): String? = driverIdStore.get()

    fun setCurrentDriverId(driverId: String) {
        driverIdStore.set(driverId)
    }

    suspend fun prepareDriver(
        deviceId: String,
        driverId: String,
    ): DriverPrepareResponseDto {
        val response = driverApi.prepareDriver(
            deviceId = deviceId,
            driverId = driverId,
        )
        driverIdStore.set(driverId)
        return response
    }

    suspend fun registerDriver(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverRegisterResponseDto {
        val response = driverApi.registerDriver(
            deviceId = deviceId,
            driverId = driverId,
            password = password,
        )
        driverIdStore.set(driverId)
        return response
    }

    suspend fun loginDriver(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverLoginResponseDto {
        val response = driverApi.loginDriver(
            deviceId = deviceId,
            driverId = driverId,
            password = password,
        )
        driverIdStore.set(driverId)
        return response
    }

    suspend fun deleteAccount(
        deviceId: String,
        driverId: String,
    ): AccountDeleteResponseDto {
        return driverApi.deleteAccount(
            deviceId = deviceId,
            driverId = driverId,
        )
    }

    fun clearDriver() {
        driverIdStore.clear()
    }
}