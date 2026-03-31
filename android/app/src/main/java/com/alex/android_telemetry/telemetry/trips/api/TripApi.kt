package com.alex.android_telemetry.telemetry.trips.api

interface TripApi {
    suspend fun fetchRecentTrips(deviceId: String, driverId: String, limit: Int = 30): List<TripSummaryDto>
    suspend fun fetchDriverHome(deviceId: String, driverId: String?): DriverHomeResponseDto
    suspend fun fetchTripReport(deviceId: String, sessionId: String, driverId: String): TripReportDto
    suspend fun performFinishTrip(pending: PendingTripFinishDto): TripReportDto
}