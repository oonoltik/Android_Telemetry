package com.alex.android_telemetry.telemetry.trips.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class OkHttpTripApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = false
    }

    @Test
    fun performFinishTrip_posts_contract_payload_without_persistence_metadata() = runBlocking {
        val unauthorizedCalled = AtomicBoolean(false)
        val interceptor = CapturingFinishInterceptor()

        val api = OkHttpTripApi(
            baseUrl = "https://example.test",
            authTokenProvider = { deviceId ->
                assertEquals("device-1", deviceId)
                "test-token"
            },
            onUnauthorized = {
                unauthorizedCalled.set(true)
            },
            client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build(),
            json = json,
        )

        val report = api.performFinishTrip(pendingFinish())

        assertEquals("session-1", report.sessionId)
        assertEquals("driver-1", report.driverId)
        assertEquals("device-1", report.deviceId)
        assertFalse("onUnauthorized must not be called for HTTP 200", unauthorizedCalled.get())

        assertEquals("POST", interceptor.method)
        assertEquals("https://example.test/trip/finish", interceptor.url)
        assertEquals("Bearer test-token", interceptor.authorization)
        assertEquals("application/json", interceptor.contentType)

        val body = interceptor.body
        assertNotNull(body)

        body ?: return@runBlocking

        val root = json.parseToJsonElement(body).jsonObject

        listOf(
            "session_id",
            "driver_id",
            "device_id",
            "client_ended_at",
            "trip_core",
            "device_meta",
            "device_context",
            "tail_activity_context",
            "tracking_mode",
            "transport_mode",
            "trip_duration_sec",
            "finish_reason",
            "app_version",
            "app_build",
            "ios_version",
            "device_model",
            "locale",
            "timezone",
            "client_metrics",
            "trip_summary",
            "trip_metrics_raw"
        ).forEach { key ->
            assertTrue("Missing finish wire key $key in $body", root.containsKey(key))
        }

        listOf(
            "created_at",
            "retry_count",
            "last_attempt_at",
            "last_error",
            "queued_because_no_delivered_batches",
            "device_context_json",
            "tail_activity_context_json",
            "tripCore",
            "deviceMeta",
            "clientMetrics",
            "tripSummary",
            "tripMetricsRaw"
        ).forEach { badKey ->
            assertFalse("Persistence/non-contract key leaked: $badKey in $body", body.contains("\"$badKey\""))
        }

        assertEquals("session-1", root["session_id"]!!.jsonPrimitive.content)
        assertEquals("driver-1", root["driver_id"]!!.jsonPrimitive.content)
        assertEquals("device-1", root["device_id"]!!.jsonPrimitive.content)
        assertEquals("2026-04-30T12:10:00Z", root["client_ended_at"]!!.jsonPrimitive.content)

        assertTrue(root["trip_core"] is JsonObject)
        assertTrue(root["device_meta"] is JsonObject)
        assertTrue(root["device_context"] is JsonObject)
        assertTrue(root["tail_activity_context"] is JsonObject)
        assertTrue(root["client_metrics"] is JsonObject)
        assertTrue(root["trip_summary"] is JsonObject)
        assertTrue(root["trip_metrics_raw"] is JsonObject)

        assertEquals(
            "foreground",
            root["device_context"]!!.jsonObject["app_state"]!!.jsonPrimitive.content
        )

        assertEquals(
            "automotive",
            root["tail_activity_context"]!!.jsonObject["dominant"]!!.jsonPrimitive.content
        )

        assertEquals(
            "android",
            root["device_meta"]!!.jsonObject["platform"]!!.jsonPrimitive.content
        )

        assertEquals(
            "session-1",
            root["trip_core"]!!.jsonObject["session_id"]!!.jsonPrimitive.content
        )

        assertFalse("NaN leaked into finish payload", body.contains("NaN"))
        assertFalse("Infinity leaked into finish payload", body.contains("Infinity"))
    }

    @Test
    fun performFinishTrip_calls_onUnauthorized_for_401() {
        val unauthorizedCalled = AtomicBoolean(false)

        val api = OkHttpTripApi(
            baseUrl = "https://example.test",
            authTokenProvider = { "test-token" },
            onUnauthorized = {
                unauthorizedCalled.set(true)
            },
            client = OkHttpClient.Builder()
                .addInterceptor(
                    StaticResponseInterceptor(
                        code = 401,
                        body = """{"detail":"unauthorized"}"""
                    )
                )
                .build(),
            json = json,
        )

        try {
            runBlocking {
                api.performFinishTrip(pendingFinish())
            }
        } catch (_: TripApiException) {
            // expected
        }

        assertTrue("onUnauthorized must be called for HTTP 401", unauthorizedCalled.get())
    }

    private class CapturingFinishInterceptor : Interceptor {
        var method: String? = null
            private set

        var url: String? = null
            private set

        var authorization: String? = null
            private set

        var contentType: String? = null
            private set

        var body: String? = null
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            method = request.method
            url = request.url.toString()
            authorization = request.header("Authorization")
            contentType = request.header("Content-Type")?.substringBefore(";")

            val buffer = Buffer()
            request.body?.writeTo(buffer)
            body = buffer.readUtf8()

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    {
                      "session_id": "session-1",
                      "driver_id": "driver-1",
                      "device_id": "device-1"
                    }
                    """.trimIndent().toResponseBody("application/json".toMediaType())
                )
                .build()
        }
    }

    private class StaticResponseInterceptor(
        private val code: Int,
        private val body: String,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("HTTP $code")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun pendingFinish(): PendingTripFinishDto {
        val brake = ClientAggDto(
            count = 1,
            sumIntensity = 0.24,
            maxIntensity = 0.24,
            countPerKm = 0.1,
            sumPerKm = 0.024,
        )

        val accel = ClientAggDto(
            count = 2,
            sumIntensity = 0.48,
            maxIntensity = 0.30,
            countPerKm = 0.2,
            sumPerKm = 0.048,
        )

        val road = ClientAggDto(
            count = 1,
            sumIntensity = 0.72,
            maxIntensity = 0.72,
            countPerKm = 0.1,
            sumPerKm = 0.072,
        )

        val turn = ClientAggDto(
            count = 3,
            sumIntensity = 0.90,
            maxIntensity = 0.40,
            countPerKm = 0.3,
            sumPerKm = 0.09,
        )

        return PendingTripFinishDto(
            sessionId = "session-1",
            driverId = "driver-1",
            deviceId = "device-1",
            clientEndedAt = "2026-04-30T12:10:00Z",
            createdAt = "2026-04-30T12:10:01Z",

            tripCore = TripCoreDto(
                tripId = "session-1",
                sessionId = "session-1",
                clientEndedAt = "2026-04-30T12:10:00Z",
            ),

            deviceMeta = DeviceMetaDto(
                platform = "android",
                appVersion = "1.0",
                appBuild = "1",
                iosVersion = "Android 15",
                deviceModel = "Pixel 8",
                locale = "ru-RU",
                timezone = "Europe/Moscow",
            ),

            trackingMode = "single_trip",
            transportMode = "car",
            tripDurationSec = 600.0,
            finishReason = "manual_stop",

            appVersion = "1.0",
            appBuild = "1",
            iosVersion = "Android 15",
            deviceModel = "Pixel 8",
            locale = "ru-RU",
            timezone = "Europe/Moscow",

            clientMetrics = ClientTripMetricsDto(
                tripDistanceM = 10_000.0,
                tripDistanceKmFromGps = 10.0,
                brake = brake,
                accel = accel,
                road = road,
                turn = turn,
            ),

            tripSummary = TripSummaryPayloadDto(
                scoreV2 = 92.0,
                drivingLoad = 1.2,
                distanceKm = 10.0,
                avgSpeedKmh = 60.0,
                drivingMode = "city",
                tripDurationSec = 600.0,
            ),

            tripMetricsRaw = TripMetricsRawDto(
                tripDistanceM = 10_000.0,
                tripDistanceKmFromGps = 10.0,
                brake = brake,
                accel = accel,
                turn = turn,
                road = road,
            ),

            deviceContext = kotlinx.serialization.json.buildJsonObject {
                put("battery_level", kotlinx.serialization.json.JsonPrimitive(0.8))
                put("battery_state", kotlinx.serialization.json.JsonPrimitive("unplugged"))
                put("low_power_mode", kotlinx.serialization.json.JsonPrimitive(false))
                put("app_state", kotlinx.serialization.json.JsonPrimitive("foreground"))
                put("screen_interaction_in_app", kotlinx.serialization.json.JsonPrimitive(true))
            },

            tailActivityContext = kotlinx.serialization.json.buildJsonObject {
                put("dominant", kotlinx.serialization.json.JsonPrimitive("automotive"))
                put("automotive_share", kotlinx.serialization.json.JsonPrimitive(0.95))
                put("walking_share", kotlinx.serialization.json.JsonPrimitive(0.02))
            },

            retryCount = 2,
            lastAttemptAt = "2026-04-30T12:11:00Z",
            lastError = "HTTP 500",
            queuedBecauseNoDeliveredBatches = true,
        )
    }
}