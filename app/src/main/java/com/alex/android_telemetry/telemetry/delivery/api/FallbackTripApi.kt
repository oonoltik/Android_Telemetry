package com.alex.android_telemetry.telemetry.trips.api

import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryPolicy
import com.alex.android_telemetry.telemetry.delivery.TelemetryRetryDecider
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.alex.android_telemetry.telemetry.delivery.DeliveryFailure
import com.alex.android_telemetry.telemetry.delivery.RetryDecision



class FallbackTripApi(
    private val primary: TripApi,
    private val fallback: TripApi,
    policy: TelemetryDeliveryPolicy = TelemetryDeliveryPolicy(),
) : TripApi {

    private val retryDecider = TelemetryRetryDecider(policy)

    override suspend fun performFinishTrip(
        pending: PendingTripFinishDto,
    ): TripReportDto {
        return tryPrimaryThenFallback(
            primaryCall = { primary.performFinishTrip(pending) },
            fallbackCall = { fallback.performFinishTrip(pending) },
        )
    }

    override suspend fun fetchTripReport(
        deviceId: String,
        sessionId: String,
        driverId: String,
    ): TripReportDto {
        return tryPrimaryThenFallback(
            primaryCall = { primary.fetchTripReport(deviceId, sessionId, driverId) },
            fallbackCall = { fallback.fetchTripReport(deviceId, sessionId, driverId) },
        )
    }

    override suspend fun fetchRecentTrips(
        deviceId: String,
        driverId: String,
        limit: Int,
    ): List<TripSummaryDto> {
        return tryPrimaryThenFallback(
            primaryCall = { primary.fetchRecentTrips(deviceId, driverId, limit) },
            fallbackCall = { fallback.fetchRecentTrips(deviceId, driverId, limit) },
        )
    }

    override suspend fun fetchDriverHome(
        deviceId: String,
        driverId: String?,
    ): DriverHomeResponseDto {
        return tryPrimaryThenFallback(
            primaryCall = { primary.fetchDriverHome(deviceId, driverId) },
            fallbackCall = { fallback.fetchDriverHome(deviceId, driverId) },
        )
    }

    private suspend fun <T> tryPrimaryThenFallback(
        primaryCall: suspend () -> T,
        fallbackCall: suspend () -> T,
    ): T {
        return try {
            primaryCall()
        } catch (t: Throwable) {
            if (!shouldFallback(t)) throw t
            fallbackCall()
        }
    }

    private fun shouldFallback(t: Throwable): Boolean {
        return when (t) {
            is SocketTimeoutException,
            is UnknownHostException,
            is IOException -> true

            is TripApiException -> when (
                retryDecider.decide(
                    failure = DeliveryFailure.Http(
                        code = t.code,
                        body = t.message,
                    ),
                    attemptCount = 0,
                )
            ) {
                is RetryDecision.Retry -> true
                is RetryDecision.FailTerminal -> false
                is RetryDecision.FailAuth -> false
            }

            else -> false
        }
    }
}