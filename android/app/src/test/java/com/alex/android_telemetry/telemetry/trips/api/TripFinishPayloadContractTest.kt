package com.alex.android_telemetry.telemetry.trips.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFinishPayloadContractTest {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = false
    }

    @Test
    fun pending_finish_dto_contains_contract_fields_and_retry_metadata() {
        val pending = pendingFinish()
        val encoded = json.encodeToString(PendingTripFinishDto.serializer(), pending)

        listOf(
            "session_id",
            "driver_id",
            "device_id",
            "client_ended_at",
            "created_at",
            "trip_core",
            "device_meta",
            "tracking_mode",
            "transport_mode",
            "trip_duration_sec",
            "finish_reason",
            "client_metrics",
            "trip_summary",
            "trip_metrics_raw",
            "device_context",
            "tail_activity_context",
            "app_version",
            "app_build",
            "ios_version",
            "device_model",
            "locale",
            "timezone",
            "retry_count",
            "last_attempt_at",
            "last_error",
            "queued_because_no_delivered_batches"
        ).forEach { key ->
            assertTrue("Missing key $key in $encoded", encoded.contains("\"$key\""))
        }

        listOf(
            "device_context_json",
            "tail_activity_context_json",
            "tripCore",
            "deviceMeta",
            "clientMetrics",
            "tripSummary",
            "tripMetricsRaw",
            "retryCount",
            "lastAttemptAt",
            "lastError"
        ).forEach { badKey ->
            assertFalse("Bad key leaked: $badKey in $encoded", encoded.contains("\"$badKey\""))
        }
    }

    @Test
    fun finish_wire_payload_uses_server_contract_keys_only() {
        val payload = buildFinishPayloadForContractTest(pendingFinish())
        val encoded = json.encodeToString(JsonObject.serializer(), payload)

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
            assertTrue("Missing finish wire key $key in $encoded", encoded.contains("\"$key\""))
        }

        listOf(
            "created_at",
            "retry_count",
            "last_attempt_at",
            "last_error",
            "queued_because_no_delivered_batches",
            "device_context_json",
            "tail_activity_context_json"
        ).forEach { badKey ->
            assertFalse("Non-wire/persistence key leaked: $badKey in $encoded", encoded.contains("\"$badKey\""))
        }

        val root = json.parseToJsonElement(encoded).jsonObject

        assertEquals("session-1", root["session_id"]?.jsonPrimitive?.content)
        assertEquals("driver-1", root["driver_id"]?.jsonPrimitive?.content)
        assertEquals("device-1", root["device_id"]?.jsonPrimitive?.content)
        assertEquals("2026-04-30T12:10:00Z", root["client_ended_at"]?.jsonPrimitive?.content)

        assertEquals("single_trip", root["tracking_mode"]?.jsonPrimitive?.content)
        assertEquals("car", root["transport_mode"]?.jsonPrimitive?.content)
        assertEquals("manual_stop", root["finish_reason"]?.jsonPrimitive?.content)

        assertNotNull(root["trip_core"])
        assertNotNull(root["device_meta"])
        assertNotNull(root["client_metrics"])
        assertNotNull(root["trip_summary"])
        assertNotNull(root["trip_metrics_raw"])
        assertNotNull(root["device_context"])
        assertNotNull(root["tail_activity_context"])
    }

    @Test
    fun finish_payload_keeps_json_contexts_as_objects_not_strings() {
        val payload = buildFinishPayloadForContractTest(pendingFinish())
        val encoded = json.encodeToString(JsonObject.serializer(), payload)
        val root = json.parseToJsonElement(encoded).jsonObject

        val deviceContext = root["device_context"]
        val tailActivityContext = root["tail_activity_context"]

        assertTrue("device_context must be JSON object in $encoded", deviceContext is JsonObject)
        assertTrue("tail_activity_context must be JSON object in $encoded", tailActivityContext is JsonObject)

        assertEquals(
            "foreground",
            root["device_context"]!!.jsonObject["app_state"]!!.jsonPrimitive.content
        )
        assertEquals(
            "automotive",
            root["tail_activity_context"]!!.jsonObject["dominant"]!!.jsonPrimitive.content
        )

        assertFalse(encoded.contains("\"device_context\":\""))
        assertFalse(encoded.contains("\"tail_activity_context\":\""))
    }

    @Test
    fun trip_core_and_device_meta_are_consistent_with_top_level_fields() {
        val pending = pendingFinish()
        val payload = buildFinishPayloadForContractTest(pending)
        val root = payload

        val tripCore = root["trip_core"]!!.jsonObject
        assertEquals(pending.sessionId, tripCore["session_id"]!!.jsonPrimitive.content)
        assertEquals(pending.clientEndedAt, tripCore["client_ended_at"]!!.jsonPrimitive.content)
        assertEquals(pending.sessionId, tripCore["trip_id"]!!.jsonPrimitive.content)

        val deviceMeta = root["device_meta"]!!.jsonObject
        assertEquals("android", deviceMeta["platform"]!!.jsonPrimitive.content)
        assertEquals(pending.appVersion, deviceMeta["app_version"]!!.jsonPrimitive.content)
        assertEquals(pending.appBuild, deviceMeta["app_build"]!!.jsonPrimitive.content)
        assertEquals(pending.iosVersion, deviceMeta["ios_version"]!!.jsonPrimitive.content)
        assertEquals(pending.deviceModel, deviceMeta["device_model"]!!.jsonPrimitive.content)
        assertEquals(pending.locale, deviceMeta["locale"]!!.jsonPrimitive.content)
        assertEquals(pending.timezone, deviceMeta["timezone"]!!.jsonPrimitive.content)
    }

    private fun buildFinishPayloadForContractTest(pending: PendingTripFinishDto): JsonObject {
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

            deviceContext = buildJsonObject {
                put("battery_level", 0.8)
                put("battery_state", "unplugged")
                put("low_power_mode", false)
                put("app_state", "foreground")
                put("screen_interaction_in_app", true)
            },

            tailActivityContext = buildJsonObject {
                put("dominant", "automotive")
                put("automotive_share", 0.95)
                put("walking_share", 0.02)
            },

            retryCount = 2,
            lastAttemptAt = "2026-04-30T12:11:00Z",
            lastError = "HTTP 500",
            queuedBecauseNoDeliveredBatches = true,
        )
    }
}