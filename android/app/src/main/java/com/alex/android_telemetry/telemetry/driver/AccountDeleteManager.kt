package com.alex.android_telemetry.telemetry.driver

import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import com.alex.android_telemetry.telemetry.driver.api.AccountDeleteResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AccountDeleteResult {
    data class Success(
        val driverId: String,
        val status: String,
    ) : AccountDeleteResult()

    data class Failed(
        val error: Throwable,
        val message: String? = error.message ?: error.toString(),
    ) : AccountDeleteResult()
}

class AccountDeleteManager(
    private val driverRepository: DriverRepository,
    private val authManager: TelemetryAuthManager,
) {
    suspend fun delete(
        deviceId: String,
        driverId: String,
    ): AccountDeleteResult {
        return withContext(Dispatchers.IO) {
            try {
                val response: AccountDeleteResponseDto = driverRepository.deleteAccount(
                    deviceId = deviceId,
                    driverId = driverId,
                )

                authManager.clearAllAuthState()
                driverRepository.clearDriver()

                AccountDeleteResult.Success(
                    driverId = driverId,
                    status = response.status,
                )
            } catch (t: Throwable) {
                AccountDeleteResult.Failed(
                    error = t,
                    message = t.message ?: t.toString(),
                )
            }
        }
    }
}