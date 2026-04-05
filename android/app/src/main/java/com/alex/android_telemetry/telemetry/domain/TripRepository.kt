package com.alex.android_telemetry.telemetry.domain

import com.alex.android_telemetry.telemetry.trips.api.DriverHomeResponseDto
import com.alex.android_telemetry.telemetry.trips.api.FinishCommand
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryDto

sealed class TripFinishResult {
    data class Sent(val report: TripReportDto) : TripFinishResult()
    data class Queued(
        val placeholderReport: TripReportDto?,
        val reason: String? = null,
    ) : TripFinishResult()
    data class Failed(
        val error: Throwable,
        val message: String? = error.message,
    ) : TripFinishResult()
}

class TripRepository(
    private val tripApi: TripApi,
    private val tripFinishManager: TripFinishManager,
) {
    suspend fun fetchRecentTrips(deviceId: String, driverId: String, limit: Int = 30): List<TripSummaryDto> {
        return tripApi.fetchRecentTrips(deviceId, driverId, limit)
    }

    suspend fun fetchDriverHome(deviceId: String, driverId: String?): DriverHomeResponseDto {
        return tripApi.fetchDriverHome(deviceId, driverId)
    }

    suspend fun fetchTripReport(deviceId: String, sessionId: String, driverId: String): TripReportDto {
        return tripApi.fetchTripReport(deviceId, sessionId, driverId)
    }

    suspend fun finishTrip(command: FinishCommand): TripFinishResult {
        return tripFinishManager.finishTrip(command)
    }

    suspend fun retryPendingFinishes() {
        tripFinishManager.retryPendingFinishes()
    }
}