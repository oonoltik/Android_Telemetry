package com.alex.android_telemetry.telemetry.domain

import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.DriverHomeResponseDto
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryDto

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

    suspend fun finishTrip(
        sessionId: String,
        driverId: String,
        deviceId: String,
        trackingMode: String? = null,
        transportMode: String? = null,
        clientEndedAt: String? = null,
        tripDurationSec: Double? = null,
        finishReason: String? = null,
        clientMetrics: ClientTripMetricsDto? = null,
        deviceContextJson: String? = null,
        tailActivityContextJson: String? = null,
    ): TripReportDto {
        return tripFinishManager.finishTrip(
            sessionId = sessionId,
            driverId = driverId,
            deviceId = deviceId,
            trackingMode = trackingMode,
            transportMode = transportMode,
            clientEndedAt = clientEndedAt,
            tripDurationSec = tripDurationSec,
            finishReason = finishReason,
            clientMetrics = clientMetrics,
            deviceContextJson = deviceContextJson,
            tailActivityContextJson = tailActivityContextJson,
        )
    }

    suspend fun retryPendingFinishes() {
        tripFinishManager.retryPendingFinishes()
    }
}