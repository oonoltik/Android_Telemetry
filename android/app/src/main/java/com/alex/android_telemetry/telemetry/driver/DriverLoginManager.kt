package com.alex.android_telemetry.telemetry.driver

import com.alex.android_telemetry.telemetry.driver.api.DriverLoginResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DriverLoginResult {
    data class Success(
        val driverId: String,
        val status: String,
    ) : DriverLoginResult()

    data class Failed(
        val error: Throwable,
        val message: String? = error.message ?: error.toString(),
    ) : DriverLoginResult()
}

class DriverLoginManager(
    private val driverRepository: DriverRepository,
) {
    suspend fun login(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverLoginResult {
        return withContext(Dispatchers.IO) {
            try {
                val response: DriverLoginResponseDto = driverRepository.loginDriver(
                    deviceId = deviceId,
                    driverId = driverId,
                    password = password,
                )

                DriverLoginResult.Success(
                    driverId = driverId,
                    status = response.status,
                )
            } catch (t: Throwable) {
                DriverLoginResult.Failed(
                    error = t,
                    message = t.message ?: t.toString(),
                )
            }
        }
    }
}