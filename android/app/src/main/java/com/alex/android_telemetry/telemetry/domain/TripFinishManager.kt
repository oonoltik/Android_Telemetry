package com.alex.android_telemetry.telemetry.domain

import android.os.Build
import android.util.Log
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import com.alex.android_telemetry.telemetry.trips.api.ClientAggDto
import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.DeviceMetaDto
import com.alex.android_telemetry.telemetry.trips.api.FinishCommand
import com.alex.android_telemetry.telemetry.trips.api.PendingTripFinishDto
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripCoreDto
import com.alex.android_telemetry.telemetry.trips.api.TripMetricsRawDto
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryPayloadDto
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryScheduler
import com.alex.android_telemetry.telemetry.trips.storage.PendingTripFinishStore
import com.alex.android_telemetry.telemetry.trips.storage.TripDeliveryStatsStore
import kotlinx.datetime.Clock
import java.util.Locale
import java.util.TimeZone
import kotlin.math.exp

class TripFinishManager(
    private val tripApi: TripApi,
    private val pendingStore: PendingTripFinishStore,
    private val deliveryStatsStore: TripDeliveryStatsStore,
    private val finishRetryScheduler: FinishRetryScheduler,
) {
    suspend fun finishTrip(command: FinishCommand): TripFinishResult {
        val pending = buildPending(command)
        val deliveryStats = deliveryStatsStore.get(command.sessionId)
        val deliveredBatches = deliveryStats.deliveredBatches

        Log.d(
            "TelemetryTrip",
            "finishTrip(): sessionId=${command.sessionId} deliveredBatches=$deliveredBatches queuedBecauseNoDeliveredBatches=${pending.queuedBecauseNoDeliveredBatches} retryCount=${pending.retryCount}"
        )

        if (deliveredBatches <= 0) {
            val queued = pending.copy(
                queuedBecauseNoDeliveredBatches = true,
                lastError = "No delivered ingest batches yet",
            )

            pendingStore.upsert(queued)

            Log.w(
                "TelemetryTrip",
                "finishTrip(): queued pending finish sessionId=${queued.sessionId} deliveredBatches=$deliveredBatches queuedBecauseNoDeliveredBatches=${queued.queuedBecauseNoDeliveredBatches} retryCount=${queued.retryCount}"
            )

            finishRetryScheduler.scheduleImmediate()

            return TripFinishResult.Queued(
                placeholderReport = queuedReport(command),
                reason = "waiting_for_first_delivered_batch"
            )
        }

        return try {
            Log.d(
                "TelemetryTrip",
                "finishTrip(): sending immediately sessionId=${pending.sessionId} deliveredBatches=$deliveredBatches retryCount=${pending.retryCount}"
            )

            val report = performFinishTrip(
                pending = pending,
                attempt = 0,
                storePendingOnFailure = true,
            )

            TripFinishResult.Sent(report)
        } catch (t: Throwable) {
            val failed = pending.copy(
                lastError = t.message,
                queuedBecauseNoDeliveredBatches = false,
            )

            pendingStore.upsert(failed)

            Log.e(
                "TelemetryTrip",
                "finishTrip(): send failed, stored pending sessionId=${failed.sessionId} deliveredBatches=$deliveredBatches queuedBecauseNoDeliveredBatches=${failed.queuedBecauseNoDeliveredBatches} retryCount=${failed.retryCount} error=${t.message}",
                t
            )

            finishRetryScheduler.scheduleImmediate()

            TripFinishResult.Queued(
                placeholderReport = queuedReport(command),
                reason = t.message ?: "retryable_finish_error"
            )
        }
    }

    suspend fun performFinishTrip(
        pending: PendingTripFinishDto,
        attempt: Int = 0,
        storePendingOnFailure: Boolean = true,
    ): TripReportDto {
        Log.d(
            "TelemetryTrip",
            "performFinishTrip(): attemptStart sessionId=${pending.sessionId} retryCount=${pending.retryCount} queuedBecauseNoDeliveredBatches=${pending.queuedBecauseNoDeliveredBatches} attempt=$attempt"
        )

        pendingStore.markAttempt(
            sessionId = pending.sessionId,
            attemptedAt = Clock.System.now().toString(),
            errorMessage = null,
        )

        return try {
            val report = tripApi.performFinishTrip(pending)

            Log.d(
                "TelemetryTrip",
                "performFinishTrip(): success sessionId=${pending.sessionId} reportSessionId=${report.sessionId} retryCount=${pending.retryCount}"
            )

            pendingStore.remove(pending.sessionId)

            Log.d(
                "TelemetryTrip",
                "performFinishTrip(): pending removed sessionId=${pending.sessionId}"
            )

            report
        } catch (t: Throwable) {
            if (storePendingOnFailure) {
                val updated = pending.copy(
                    retryCount = pending.retryCount + 1,
                    lastAttemptAt = Clock.System.now().toString(),
                    lastError = t.message,
                )

                pendingStore.upsert(updated)

                Log.e(
                    "TelemetryTrip",
                    "performFinishTrip(): failed and re-stored pending sessionId=${updated.sessionId} retryCount=${updated.retryCount} queuedBecauseNoDeliveredBatches=${updated.queuedBecauseNoDeliveredBatches} error=${t.message}",
                    t
                )

                finishRetryScheduler.scheduleImmediate()
            } else {
                Log.e(
                    "TelemetryTrip",
                    "performFinishTrip(): failed without storing pending sessionId=${pending.sessionId} retryCount=${pending.retryCount} error=${t.message}",
                    t
                )
            }

            throw t
        }
    }

    suspend fun retryPendingFinishes() {
        val items = pendingStore.getAll()

        Log.d(
            "TelemetryTrip",
            "retryPendingFinishes(): foundPending=${items.size}"
        )

        for (item in items) {
            val deliveryStats = deliveryStatsStore.get(item.sessionId)
            val deliveredBatches = deliveryStats.deliveredBatches

            Log.d(
                "TelemetryTrip",
                "retryPendingFinishes(): inspecting sessionId=${item.sessionId} deliveredBatches=$deliveredBatches queuedBecauseNoDeliveredBatches=${item.queuedBecauseNoDeliveredBatches} retryCount=${item.retryCount}"
            )

            if (deliveredBatches <= 0) {
                Log.d(
                    "TelemetryTrip",
                    "retryPendingFinishes(): skipped sessionId=${item.sessionId} deliveredBatches=$deliveredBatches queuedBecauseNoDeliveredBatches=${item.queuedBecauseNoDeliveredBatches} retryCount=${item.retryCount}"
                )
                continue
            }

            runCatching {
                performFinishTrip(
                    pending = item.copy(queuedBecauseNoDeliveredBatches = false),
                    attempt = item.retryCount + 1,
                    storePendingOnFailure = true,
                )
            }.onFailure {
                pendingStore.markAttempt(
                    sessionId = item.sessionId,
                    attemptedAt = Clock.System.now().toString(),
                    errorMessage = it.message,
                )

                Log.e(
                    "TelemetryTrip",
                    "retryPendingFinishes(): retry failed sessionId=${item.sessionId} deliveredBatches=$deliveredBatches retryCount=${item.retryCount + 1} error=${it.message}",
                    it
                )
            }
        }
    }

    private fun buildPending(command: FinishCommand): PendingTripFinishDto {
        val safeMetrics = command.clientMetrics?.let(::sanitizeMetrics)
        val tripMetricsRaw = safeMetrics?.toTripMetricsRawDto()
        val tripSummary = command.tripSummary ?: safeMetrics?.let {
            publicAlphaSummary(it, command.tripDurationSec)
        }

        return PendingTripFinishDto(
            sessionId = command.sessionId,
            driverId = command.driverId.trim(),
            deviceId = command.deviceId,
            clientEndedAt = command.clientEndedAt,
            createdAt = Clock.System.now().toString(),
            tripCore = TripCoreDto(
                tripId = command.sessionId,
                sessionId = command.sessionId,
                clientEndedAt = command.clientEndedAt,
            ),
            deviceMeta = DeviceMetaDto(
                platform = "Android",
                appVersion = BuildConfig.VERSION_NAME,
                appBuild = BuildConfig.VERSION_CODE.toString(),
                iosVersion = Build.VERSION.RELEASE,
                deviceModel = Build.MODEL,
                locale = Locale.getDefault().toLanguageTag(),
                timezone = TimeZone.getDefault().id,
            ),
            trackingMode = command.trackingMode,
            transportMode = command.transportMode,
            tripDurationSec = command.tripDurationSec?.let { NumericSanitizer.metric(it) },
            finishReason = command.finishReason,
            clientMetrics = safeMetrics,
            tripSummary = tripSummary,
            tripMetricsRaw = tripMetricsRaw,
            deviceContext = command.deviceContext,
            tailActivityContext = command.tailActivityContext,
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            iosVersion = Build.VERSION.RELEASE,
            deviceModel = Build.MODEL,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
        )
    }

    private fun sanitizeMetrics(metrics: ClientTripMetricsDto): ClientTripMetricsDto {
        fun agg(a: ClientAggDto): ClientAggDto {
            return ClientAggDto(
                count = a.count,
                sumIntensity = NumericSanitizer.metric(a.sumIntensity),
                maxIntensity = NumericSanitizer.metric(a.maxIntensity),
                countPerKm = NumericSanitizer.metric(a.countPerKm),
                sumPerKm = NumericSanitizer.metric(a.sumPerKm),
            )
        }

        return ClientTripMetricsDto(
            tripDistanceM = NumericSanitizer.metric(metrics.tripDistanceM),
            tripDistanceKmFromGps = NumericSanitizer.metric(metrics.tripDistanceKmFromGps),
            brake = agg(metrics.brake),
            accel = agg(metrics.accel),
            road = agg(metrics.road),
            turn = agg(metrics.turn),
        )
    }

    private fun ClientTripMetricsDto.toTripMetricsRawDto(): TripMetricsRawDto {
        return TripMetricsRawDto(
            tripDistanceM = tripDistanceM,
            tripDistanceKmFromGps = tripDistanceKmFromGps,
            brake = brake,
            accel = accel,
            turn = turn,
            road = road,
        )
    }

    private fun publicAlphaSummary(
        metrics: ClientTripMetricsDto,
        durationSec: Double?,
    ): TripSummaryPayloadDto {
        fun load(agg: ClientAggDto): Double {
            if (agg.count <= 0) return 0.0
            val mean = agg.sumIntensity / agg.count.toDouble()
            return NumericSanitizer.metric(agg.count.toDouble() * mean * mean)
        }

        val distanceKm = maxOf(0.001, metrics.tripDistanceKmFromGps)
        val tripLoad = load(metrics.brake) + load(metrics.accel) + load(metrics.turn) + load(metrics.road)
        val drivingLoad = NumericSanitizer.metric(tripLoad / distanceKm)
        val scoreV2 = NumericSanitizer.metric(100.0 * exp(-0.15 * drivingLoad), digits = 2)

        val avgSpeedKmh = if (durationSec != null && durationSec > 0.0) {
            val hours = durationSec / 3600.0
            if (hours > 0.0) NumericSanitizer.metric(distanceKm / hours, digits = 2) else 0.0
        } else {
            0.0
        }

        val drivingMode = when {
            avgSpeedKmh >= 60.0 -> "Highway"
            avgSpeedKmh > 0.0 && avgSpeedKmh <= 35.0 -> "City"
            else -> "Mixed"
        }

        return TripSummaryPayloadDto(
            scoreV2 = scoreV2,
            drivingLoad = drivingLoad,
            distanceKm = NumericSanitizer.metric(distanceKm),
            avgSpeedKmh = avgSpeedKmh,
            drivingMode = drivingMode,
            tripDurationSec = NumericSanitizer.metric(durationSec ?: 0.0),
        )
    }

    private fun queuedReport(command: FinishCommand): TripReportDto {
        Log.d(
            "TelemetryTrip",
            "queuedReport(): sessionId=${command.sessionId} deviceId=${command.deviceId}"
        )

        return TripReportDto(
            sessionId = command.sessionId,
            driverId = command.driverId,
            deviceId = command.deviceId,
            clientEndedAt = command.clientEndedAt,
            tripDurationSec = command.tripDurationSec,
            tripScore = 0.0,
            worstBatchScore = 0.0,
            batchesCount = 0,
            samplesCount = 0,
            eventsCount = 0,
        )
    }
}