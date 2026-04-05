package com.alex.android_telemetry.telemetry.session

import android.os.Build
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.model.FinishPayloadDraft
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeStore
import com.alex.android_telemetry.telemetry.trips.api.ClientAggDto
import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.DeviceMetaDto
import com.alex.android_telemetry.telemetry.trips.api.TripCoreDto
import com.alex.android_telemetry.telemetry.trips.api.TripMetricsRawDto
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryPayloadDto
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class TripStopResult {
    data class Sent(val report: TripReportDto) : TripStopResult()
    data class Queued(
        val report: TripReportDto?,
        val reason: String? = null
    ) : TripStopResult()
    data class Failed(
        val error: Throwable,
        val message: String? = error.message
    ) : TripStopResult()
}

class TripSessionStopper(
    private val clockProvider: ClockProvider,
    private val tripFinishCoordinator: TripFinishCoordinator,
    private val sessionRepository: TripSessionRepository,
    private val runtimeStore: TripRuntimeStore
) {
    suspend fun stop(finishReason: FinishReason): FinishPayloadDraft? {
        val state = runtimeStore.currentState()
        val session = state.activeSession ?: return null

        val endedAt = clockProvider.nowIsoStringUtc()
        val durationSec =
            ((clockProvider.nowEpochMillis() - session.startedAtEpochMillis).coerceAtLeast(0L) / 1000.0)

        val distanceM = state.liveDistanceMeters.coerceAtLeast(0.0)
        val distanceKm = distanceM / 1000.0

        val avgSpeedKmh = if (durationSec > 0.0) {
            (distanceM / durationSec) * 3.6
        } else {
            0.0
        }

        val drivingMode = when {
            avgSpeedKmh >= 70.0 -> "Highway"
            avgSpeedKmh >= 30.0 -> "Mixed"
            else -> "City"
        }

        val zeroAgg = ClientAggDto(
            count = 0,
            sumIntensity = 0.0,
            maxIntensity = 0.0,
            countPerKm = 0.0,
            sumPerKm = 0.0,
        )

        val clientMetrics = ClientTripMetricsDto(
            tripDistanceM = distanceM,
            tripDistanceKmFromGps = distanceKm,
            brake = zeroAgg,
            accel = zeroAgg,
            road = zeroAgg,
            turn = zeroAgg,
        )

        val tripSummary = TripSummaryPayloadDto(
            scoreV2 = 100.0,
            drivingLoad = 0.0,
            distanceKm = distanceKm,
            avgSpeedKmh = avgSpeedKmh,
            drivingMode = drivingMode,
            tripDurationSec = durationSec,
        )

        val tripMetricsRaw = TripMetricsRawDto(
            tripDistanceM = distanceM,
            tripDistanceKmFromGps = distanceKm,
            brake = zeroAgg,
            accel = zeroAgg,
            turn = zeroAgg,
            road = zeroAgg,
        )

        val tripCore = TripCoreDto(
            tripId = session.sessionId,
            sessionId = session.sessionId,
            clientEndedAt = endedAt,
        )

        val deviceMeta = DeviceMetaDto(
            platform = "Android",
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            iosVersion = Build.VERSION.RELEASE,
            deviceModel = Build.MODEL,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
        )

        val deviceContext = buildJsonObject {
            put("status", state.status.toString())
            put("is_trip_active", state.isTripActive)
            put("session_id", session.sessionId)
            put("driver_id", session.driverId ?: "")
            put("device_id", session.deviceId)
            put("tracking_mode", session.trackingMode.name.lowercase())
            put("transport_mode", session.transportMode.name.lowercase())
            put("started_at", session.startedAt)
            put("ended_at", endedAt)
            put("elapsed_sec", state.counters.elapsedSec)
            put("distance_m", distanceM)
            put("avg_speed_kmh", avgSpeedKmh)
            put("samples_buffered", state.counters.samplesBuffered)
            put("batches_created", state.counters.batchesCreated)
            put("batches_delivered", state.counters.batchesDelivered)
            put("eu_delivered", state.routeStats.euDelivered)
            put("ru_delivered", state.routeStats.ruDelivered)
        }

        val tailActivityContext = buildJsonObject {
            put("source", "trip_session_stopper")
            put("finish_reason", finishReason.name.lowercase())
            put("ended_at", endedAt)
            put("last_error", state.lastError ?: "")
            put("last_report_session_id", state.lastReportSessionId ?: "")
        }

        val payload = FinishPayloadDraft(
            sessionId = session.sessionId,
            driverId = session.driverId.orEmpty(),
            deviceId = session.deviceId,
            clientEndedAt = endedAt,
            trackingMode = session.trackingMode.name.lowercase(),
            transportMode = session.transportMode.name.lowercase(),
            tripDurationSec = durationSec,
            finishReason = finishReason.name.lowercase(),
            tripCore = tripCore,
            deviceMeta = deviceMeta,
            clientMetrics = clientMetrics,
            tripSummary = tripSummary,
            tripMetricsRaw = tripMetricsRaw,
            deviceContext = deviceContext,
            tailActivityContext = tailActivityContext,
        )

        val result = tripFinishCoordinator.dispatchFinish(payload)

        runtimeStore.update {
            it.copy(
                finishPending = result.queued,
                lastReportSessionId = result.reportSessionId ?: session.sessionId,
                lastEndedAt = endedAt,
                lastError = result.error
            )
        }

        sessionRepository.clearActiveSession()
        return payload
    }
}