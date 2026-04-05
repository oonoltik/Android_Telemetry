package com.alex.android_telemetry.telemetry.trips.api

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

        Log.d("TelemetryTrip", "fetchRecentTrips(): route=${uri} driverId=$driverId")

        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d(
                "TelemetryTrip",
                "fetchRecentTrips(): httpCode=${response.code} route=${uri}"
            )

            if (response.code == 401) onUnauthorized()
            if (!response.isSuccessful) {
                throw TripApiException(
                    code = response.code,
                    message = "fetchRecentTrips failed: HTTP ${response.code}: $body",
                )
            }
            json.decodeFromString(RecentTripsResponseDto.serializer(), body).trips
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

        val uri = uriBuilder.build()

        Log.d("TelemetryTrip", "fetchDriverHome(): route=$uri driverId=$trimmed")

        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d(
                "TelemetryTrip",
                "fetchDriverHome(): httpCode=${response.code} route=$uri"
            )

            if (response.code == 401) onUnauthorized()
            if (!response.isSuccessful) {
                throw TripApiException(
                    code = response.code,
                    message = "fetchDriverHome failed: HTTP ${response.code}: $body",
                )
            }
            json.decodeFromString(DriverHomeResponseDto.serializer(), body)
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

        Log.d(
            "TelemetryTrip",
            "fetchTripReport(): sessionId=$sessionId route=$uri"
        )

        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d(
                "TelemetryTrip",
                "fetchTripReport(): sessionId=$sessionId httpCode=${response.code} route=$uri"
            )

            if (response.code == 401) {
                onUnauthorized()
            }

            if (!response.isSuccessful) {
                throw TripApiException(
                    code = response.code,
                    message = "HTTP ${response.code}: $body",
                )
            }

            json.decodeFromString(TripReportDto.serializer(), body)
        }
    }

    override suspend fun performFinishTrip(
        pending: PendingTripFinishDto,
    ): TripReportDto = withContext(Dispatchers.IO) {
        val bearer = authTokenProvider(pending.deviceId)
        val route = "${baseUrl.trimEnd('/')}/trip/finish"

        val payload = buildPayload(pending)
        val payloadString = json.encodeToString(JsonObject.serializer(), payload)

        Log.d(
            "TelemetryTrip",
            "performFinishTrip(): sessionId=${pending.sessionId} retryCount=${pending.retryCount} queuedBecauseNoDeliveredBatches=${pending.queuedBecauseNoDeliveredBatches} route=$route"
        )
        Log.d("TelemetryTrip", "performFinishTrip(): payload=${payloadString.take(1600)}")

        val request = Request.Builder()
            .url(route)
            .post(payloadString.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $bearer")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            Log.d(
                "TelemetryTrip",
                "performFinishTrip(): sessionId=${pending.sessionId} httpCode=${response.code} route=$route"
            )

            if (response.code == 401) {
                onUnauthorized()
            }

            if (!response.isSuccessful) {
                Log.e(
                    "TelemetryTrip",
                    "performFinishTrip(): failed sessionId=${pending.sessionId} httpCode=${response.code} route=$route body=${body.take(1200)}"
                )

                throw TripApiException(
                    code = response.code,
                    message = "HTTP ${response.code}: $body",
                )
            }

            Log.d(
                "TelemetryTrip",
                "performFinishTrip(): success sessionId=${pending.sessionId} httpCode=${response.code} route=$route"
            )

            json.decodeFromString(TripReportDto.serializer(), body)
        }
    }

    private fun buildPayload(pending: PendingTripFinishDto): JsonObject {
        return buildJsonObject {
            put("session_id", pending.sessionId)
            put("driver_id", pending.driverId)
            put("device_id", pending.deviceId)
            put("client_ended_at", pending.clientEndedAt)

            put(
                "trip_core",
                json.encodeToJsonElement(TripCoreDto.serializer(), pending.tripCore)
            )

            put(
                "device_meta",
                json.encodeToJsonElement(DeviceMetaDto.serializer(), pending.deviceMeta)
            )

            pending.deviceContext?.let { put("device_context", it) }
            pending.tailActivityContext?.let { put("tail_activity_context", it) }

            pending.trackingMode?.let { put("tracking_mode", it) }
            pending.transportMode?.let { put("transport_mode", it) }
            pending.tripDurationSec?.let { put("trip_duration_sec", JsonPrimitive(it)) }
            pending.finishReason?.let { put("finish_reason", it) }

            pending.appVersion?.let { put("app_version", it) }
            pending.appBuild?.let { put("app_build", it) }
            pending.iosVersion?.let { put("ios_version", it) }
            pending.deviceModel?.let { put("device_model", it) }
            pending.locale?.let { put("locale", it) }
            pending.timezone?.let { put("timezone", it) }

            pending.clientMetrics?.let {
                put(
                    "client_metrics",
                    json.encodeToJsonElement(ClientTripMetricsDto.serializer(), it)
                )
            }

            pending.tripSummary?.let {
                put(
                    "trip_summary",
                    json.encodeToJsonElement(TripSummaryPayloadDto.serializer(), it)
                )
            }

            pending.tripMetricsRaw?.let {
                put(
                    "trip_metrics_raw",
                    json.encodeToJsonElement(TripMetricsRawDto.serializer(), it)
                )
            }
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}