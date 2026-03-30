package com.alex.android_telemetry.telemetry.domain

import android.os.Build
import android.util.Log
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.PendingTripFinishDto
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryScheduler
import com.alex.android_telemetry.telemetry.trips.storage.PendingTripFinishStore
import com.alex.android_telemetry.telemetry.trips.storage.TripDeliveryStatsStore
import kotlinx.datetime.Clock
import java.util.Locale
import java.util.TimeZone

class TripFinishManager(
    private val tripApi: TripApi,
    private val pendingStore: PendingTripFinishStore,
    private val deliveryStatsStore: TripDeliveryStatsStore,
    private val finishRetryScheduler: FinishRetryScheduler,
) {
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
        val endedAtIso = clientEndedAt ?: Clock.System.now().toString()

        val pending = PendingTripFinishDto(
            sessionId = sessionId,
            driverId = resolvedDriverId(driverId),
            deviceId = deviceId,
            clientEndedAt = endedAtIso,
            createdAt = Clock.System.now().toString(),
            trackingMode = trackingMode,
            transportMode = transportMode,
            tripDurationSec = tripDurationSec,
            finishReason = finishReason,
            clientMetrics = clientMetrics,
            deviceContextJson = deviceContextJson,
            tailActivityContextJson = tailActivityContextJson,
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            iosVersion = null,
            deviceModel = Build.MODEL,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
        )

        val deliveredBatches = deliveryStatsStore.get(sessionId).deliveredBatches

        Log.d(
            "TelemetryTrip",
            "finishTrip(): sessionId=$sessionId deliveredBatches=$deliveredBatches -> attempt finish now"
        )

        return try {
            performFinishTrip(
                pending = pending,
                attempt = 0,
                storePendingOnFailure = true,
            )
        } catch (t: Throwable) {
            Log.d(
                "TelemetryTrip",
                "finish queued after immediate attempt sessionId=$sessionId deliveredBatches=$deliveredBatches error=${t.message}"
            )
            TripReportDto(
                sessionId = sessionId,
                driverId = driverId,
                deviceId = deviceId,
                tripScore = 0.0,
                worstBatchScore = 0.0,
            )
        }
    }

    suspend fun performFinishTrip(
        pending: PendingTripFinishDto,
        attempt: Int = 0,
        storePendingOnFailure: Boolean = true,
    ): TripReportDto {
        return try {
            val report = tripApi.performFinishTrip(pending)
            pendingStore.remove(pending.sessionId)
            report
        } catch (t: Throwable) {
            if (storePendingOnFailure) {
                pendingStore.upsert(pending)
                finishRetryScheduler.scheduleImmediate()
            }
            Log.d(
                "TelemetryTrip",
                "finish failed attempt=$attempt sessionId=${pending.sessionId}: ${t.message}"
            )
            throw t
        }
    }

    suspend fun retryPendingFinishes() {
        val items = pendingStore.getAll()

        for (item in items) {
            val deliveredBatches = deliveryStatsStore.get(item.sessionId).deliveredBatches

            Log.d(
                "TelemetryTrip",
                "retryPendingFinishes attempt sessionId=${item.sessionId} deliveredBatches=$deliveredBatches"
            )

            runCatching {
                performFinishTrip(
                    pending = item,
                    attempt = 0,
                    storePendingOnFailure = true,
                )
            }.onFailure {
                Log.e(
                    "TelemetryTrip",
                    "retryPendingFinishes failed sessionId=${item.sessionId} deliveredBatches=$deliveredBatches",
                    it,
                )
            }
        }
    }

    private fun resolvedDriverId(driverId: String): String {
        return driverId.trim()
    }
}