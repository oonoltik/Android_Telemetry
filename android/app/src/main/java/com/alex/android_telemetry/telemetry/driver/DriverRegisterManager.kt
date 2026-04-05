package com.alex.android_telemetry.telemetry.driver

import com.alex.android_telemetry.telemetry.driver.api.DriverRegisterResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
sealed class DriverRegisterResult {
    data class Success(
        val driverId: String,
        val status: String,
    ) : DriverRegisterResult()

    data class Failed(
        val error: Throwable,
        val message: String? = error.message,
    ) : DriverRegisterResult()
}

class DriverRegisterManager(
    private val driverRepository: DriverRepository,
) {
    suspend fun register(
        deviceId: String,
        driverId: String,
        password: String,
    ): DriverRegisterResult {
        return withContext(Dispatchers.IO) {
            try {
                val response: DriverRegisterResponseDto = driverRepository.registerDriver(
                    deviceId = deviceId,
                    driverId = driverId,
                    password = password,
                )

                DriverRegisterResult.Success(
                    driverId = driverId,
                    status = response.status,
                )
            } catch (t: Throwable) {
                DriverRegisterResult.Failed(t)
            }
        }
    }
}