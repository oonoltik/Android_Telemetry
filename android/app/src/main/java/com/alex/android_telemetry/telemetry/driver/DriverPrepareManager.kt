package com.alex.android_telemetry.telemetry.driver

import com.alex.android_telemetry.telemetry.driver.api.DriverPrepareResponseDto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DriverPrepareResult {
    data class Success(
        val driverId: String,
        val status: String,
    ) : DriverPrepareResult()

    data class Failed(
        val error: Throwable,
        val message: String? = error.message,
    ) : DriverPrepareResult()
}

class DriverPrepareManager(
    private val driverRepository: DriverRepository,
) {
    suspend fun prepare(
        deviceId: String,
        driverId: String,
    ): DriverPrepareResult {
        return withContext(Dispatchers.IO) {
            try {
            val response: DriverPrepareResponseDto = driverRepository.prepareDriver(
                deviceId = deviceId,
                driverId = driverId,
            )

            DriverPrepareResult.Success(
                driverId = driverId,
                status = response.status,
            )
        } catch (t: Throwable) {
            DriverPrepareResult.Failed(t)
        }
    }
}
}