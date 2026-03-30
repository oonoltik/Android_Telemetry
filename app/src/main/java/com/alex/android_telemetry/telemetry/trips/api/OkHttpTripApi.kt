package com.alex.android_telemetry.telemetry.trips.api

import android.net.Uri
import android.util.Log
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.exp

@Serializable
private data class TripSummaryPayloadDto(
    val score_v2: Double,
    val driving_load: Double,
    val distance_km: Double,
    val avg_speed_kmh: Double,
    val driving_mode: String,
    val trip_duration_sec: Double,
)

class OkHttpTripApi(
    private val baseUrl: String,
    private val authTokenProvider: suspend (deviceId: String) -> String,
    private val onUnauthorized: suspend () -> Unit,
    private val client: OkHttpClient,
    private val json: Json,
) : TripApi {

    override suspend fun fetchRecentTrips(
        deviceId: String,
        driverId: String,
        limit: Int,
    ): List<TripSummaryDto> = withContext(Dispatchers.IO) {
        val bearer = authTokenProvider(deviceId)

        val uri = Uri.parse("${baseUrl.trimEnd('/')}/trips/recent")
            .buildUpon()
            .appendQueryParameter("driver_id", driverId)
            .appendQueryParameter("limit", limit.coerceIn(1, 30).toString())
            .build()

        val req = Request.Builder()
            .url(uri.toString())
            .get()
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401) onUnauthorized()
            if (!response.isSuccessful) error("fetchRecentTrips failed: code=${response.code} body=$body")
            json.decodeFromString<RecentTripsResponseDto>(body).trips
        }
    }

    override suspend fun fetchDriverHome(
        deviceId: String,
        driverId: String?,
    ): DriverHomeResponseDto = withContext(Dispatchers.IO) {
        val bearer = authTokenProvider(deviceId)

        val uriBuilder = Uri.parse("${baseUrl.trimEnd('/')}/driver/home").buildUpon()
        val trimmed = driverId?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            uriBuilder.appendQueryParameter("driver_id", trimmed)
        }

        val req = Request.Builder()
            .url(uriBuilder.build().toString())
            .get()
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401) onUnauthorized()
            if (!response.isSuccessful) error("fetchDriverHome failed: code=${response.code} body=$body")
            json.decodeFromString<DriverHomeResponseDto>(body)
        }
    }

    override suspend fun fetchTripReport(
        deviceId: String,
        sessionId: String,
        driverId: String,
    ): TripReportDto = withContext(Dispatchers.IO) {
        val bearer = authTokenProvider(deviceId)

        val uri = Uri.parse("${baseUrl.trimEnd('/')}/trip/report")
            .buildUpon()
            .appendQueryParameter("session_id", sessionId)
            .appendQueryParameter("driver_id", driverId)
            .build()

        val req = Request.Builder()
            .url(uri.toString())
            .get()
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (response.code == 401) {
                onUnauthorized()
            }

            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                throw TripApiException(
                    code = response.code,
                    message = "HTTP ${response.code}: $bodyText",
                )
            }

            json.decodeFromString(TripReportDto.serializer(), body)
        }
    }

    override suspend fun performFinishTrip(
        pending: PendingTripFinishDto,
    ): TripReportDto = withContext(Dispatchers.IO) {
        val bearer = authTokenProvider(pending.deviceId)

        val payload = buildPayload(pending)
        val payloadString = json.encodeToString(JsonObject.serializer(), payload)

        Log.d("TelemetryTrip", "POST /trip/finish sessionId=${pending.sessionId} payload=${payloadString.take(800)}")

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/trip/finish")
            .post(payloadString.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d("TelemetryTrip", "POST /trip/finish code=${response.code} body=${body.take(400)}")

            if (response.code == 401) {
                onUnauthorized()
            }

            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                throw TripApiException(
                    code = response.code,
                    message = "HTTP ${response.code}: $bodyText",
                )
            }

            json.decodeFromString(TripReportDto.serializer(), body)
        }
    }

    private fun buildPayload(pending: PendingTripFinishDto): JsonObject {
        Log.e("TelemetryTrip", "🔥 BUILD PAYLOAD CALLED 🔥")
        val safeMetrics = pending.clientMetrics?.let { sanitized(it) }

        val tripSummary = if (safeMetrics != null) {
            publicAlphaSummary(safeMetrics, pending.tripDurationSec)
        } else {
            Log.e("TelemetryTrip", "tripSummary NULL → creating fallback")

            TripSummaryPayloadDto(
                score_v2 = 0.0,
                driving_load = 0.0,
                distance_km = 0.0,
                avg_speed_kmh = 0.0,
                driving_mode = "Unknown",
                trip_duration_sec = pending.tripDurationSec ?: 0.0,
            )
        }

        Log.d("TelemetryTrip", "tripSummary class=${tripSummary.javaClass} value=$tripSummary")

        return buildJsonObject {
            put("session_id", pending.sessionId)
            put("driver_id", pending.driverId)
            put("device_id", pending.deviceId)
            put("client_ended_at", pending.clientEndedAt)

            putJsonObject("trip_core") {
                put("trip_id", pending.sessionId)
                put("session_id", pending.sessionId)
                put("client_ended_at", pending.clientEndedAt)
            }

            putJsonObject("device_meta") {
                put("platform", "android")
                pending.appVersion?.let { put("app_version", it) }
                pending.appBuild?.let { put("app_build", it) }
                pending.deviceModel?.let { put("device_model", it) }
                pending.locale?.let { put("locale", it) }
                pending.timezone?.let { put("timezone", it) }
            }

            decodeJsonObject(pending.deviceContextJson)?.let { put("device_context", it) }
            decodeJsonObject(pending.tailActivityContextJson)?.let { put("tail_activity_context", it) }

            pending.trackingMode?.let { put("tracking_mode", it) }
            pending.transportMode?.let { put("transport_mode", it) }
            pending.tripDurationSec?.let { put("trip_duration_sec", NumericSanitizer.metric(it)) }
            pending.finishReason?.let { put("finish_reason", it) }

            safeMetrics?.let { metrics ->
                put(
                    "client_metrics",
                    json.encodeToJsonElement(ClientTripMetricsDto.serializer(), metrics)
                )
                put(
                    "trip_metrics_raw",
                    json.encodeToJsonElement(ClientTripMetricsDto.serializer(), metrics)
                )
            }

            run {
                Log.d("TelemetryTrip", "encoding tripSummary type=${tripSummary.javaClass}")
                put(
                    "trip_summary",
                    json.encodeToJsonElement(TripSummaryPayloadDto.serializer(), tripSummary)
                )
            }

            pending.appVersion?.let { put("app_version", it) }
            pending.appBuild?.let { put("app_build", it) }
            pending.deviceModel?.let { put("device_model", it) }
            pending.locale?.let { put("locale", it) }
            pending.timezone?.let { put("timezone", it) }
        }
    }

    private fun sanitized(metrics: ClientTripMetricsDto): ClientTripMetricsDto {
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
            score_v2 = scoreV2,
            driving_load = drivingLoad,
            distance_km = NumericSanitizer.metric(distanceKm),
            avg_speed_kmh = avgSpeedKmh,
            driving_mode = drivingMode,
            trip_duration_sec = NumericSanitizer.metric(durationSec ?: 0.0),
        )
    }

    private fun decodeJsonObject(value: String?): JsonObject? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            Json.parseToJsonElement(value).jsonObject
        }.getOrNull()
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}