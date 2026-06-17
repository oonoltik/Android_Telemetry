package com.alex.android_telemetry.telemetry.ingest.mapper

import com.alex.android_telemetry.telemetry.domain.model.ActivityContextSummary
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSummary
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.MotionActivitySummary
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.PedometerSummary
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionContextSummary
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.alex.android_telemetry.telemetry.domain.model.Attitude
import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import com.alex.android_telemetry.telemetry.ingest.api.TelemetryBatchDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue


class TelemetryBatchDtoMapperTest {

    private val mapper = TelemetryBatchDtoMapper()

    private val json = Json {
        encodeDefaults = false
        prettyPrint = false
    }

    @Test
    fun map_includes_full_context_blocks_when_present() {
        val batch = TelemetryBatch(
            deviceId = "device-1",
            driverId = "driver-1",
            sessionId = "session-1",
            createdAt = Instant.parse("2026-04-06T10:15:30Z"),
            trackingMode = TrackingMode.DAY_MONITORING,
            transportMode = "car",
            batchId = "batch-1",
            batchSeq = 7,
            frames = emptyList(),
            events = emptyList(),
            deviceState = DeviceStateSnapshot(
                timestamp = Instant.parse("2026-04-06T10:15:29Z"),
                batteryLevel = 0.72,
                batteryState = "charging",
                lowPowerMode = false,
            ),
            networkState = NetworkStateSnapshot(
                timestamp = Instant.parse("2026-04-06T10:15:29Z"),
                status = "satisfied",
                interfaceType = "wifi",
                isExpensive = false,
                isConstrained = false,
            ),
            headingSummary = HeadingSample(
                timestamp = Instant.parse("2026-04-06T10:15:29Z"),
                magneticHeadingDeg = 123.0,
                trueHeadingDeg = 121.5,
                accuracyDeg = 4.0,
            ),
            motionActivitySummary = MotionActivitySummary(
                dominant = "automotive",
                confidence = "high",
                durationsSec = mapOf(
                    "automotive" to 24.0,
                    "stationary" to 3.0,
                ),
            ),
            activityContextSummary = ActivityContextSummary(
                dominant = "automotive",
                bestConfidence = "high",
                stationaryShare = 0.10,
                walkingShare = 0.0,
                runningShare = 0.0,
                cyclingShare = 0.0,
                automotiveShare = 0.90,
                unknownShare = 0.0,
                nonAutomotiveStreakSec = 0.0,
                isAutomotiveNow = true,
                windowStartedAt = Instant.parse("2026-04-06T10:15:00Z"),
                windowEndedAt = Instant.parse("2026-04-06T10:15:30Z"),
            ),
            pedometerSummary = PedometerSummary(
                steps = 42,
                distanceM = 31.5,
                cadence = 1.8,
                pace = 0.56,
            ),
            altimeterSummary = AltimeterSummary(
                relAltMMin = -1.2,
                relAltMMax = 3.4,
                pressureKpaMin = 99.8,
                pressureKpaMax = 100.3,
            ),
            screenInteractionContextSummary = ScreenInteractionContextSummary(
                count = 3,
                recent = true,
                activeSec = 12.5,
                lastAt = Instant.parse("2026-04-06T10:15:25Z"),
                windowStartedAt = Instant.parse("2026-04-06T10:15:00Z"),
                windowEndedAt = Instant.parse("2026-04-06T10:15:30Z"),
            ),
            tripConfig = EventThresholdSet(
                accelSharpG = 0.18,
                accelEmergencyG = 0.28,
                brakeSharpG = 0.22,
                brakeEmergencyG = 0.32,
                turnSharpG = 0.22,
                turnEmergencyG = 0.30,
                roadLowG = 0.45,
                roadHighG = 0.75,
                minSpeedForAccelBrakeMS = 3.0,
                minSpeedForTurnMS = 5.0,
                accelBrakeCooldownS = 1.2,
                turnCooldownS = 0.8,
                roadCooldownS = 1.0,
            ),
        )

        val dto = mapper.map(batch)

        assertEquals("device-1", dto.deviceId)
        assertEquals("driver-1", dto.driverId)
        assertEquals("session-1", dto.sessionId)
        assertEquals("day_monitoring", dto.trackingMode)
        assertEquals("car", dto.transportMode)
        assertEquals("batch-1", dto.batchId)
        assertEquals(7, dto.batchSeq)

        assertNotNull(dto.deviceState)
        assertEquals(0.72, dto.deviceState?.batteryLevel ?: 0.0, 1e-9)
        assertEquals("charging", dto.deviceState?.batteryState)
        assertEquals(false, dto.deviceState?.lowPowerMode)

        assertNotNull(dto.network)
        assertEquals("satisfied", dto.network?.status)
        assertEquals("wifi", dto.network?.interfaceName)
        assertEquals(false, dto.network?.expensive)
        assertEquals(false, dto.network?.constrained)

        assertNotNull(dto.heading)
        assertEquals(123.0, dto.heading?.magneticDeg ?: 0.0, 1e-9)
        assertEquals(121.5, dto.heading?.trueDeg ?: 0.0, 1e-9)
        assertEquals(4.0, dto.heading?.accuracyDeg ?: 0.0, 1e-9)

        assertNotNull(dto.motionActivity)
        assertEquals("automotive", dto.motionActivity?.dominant)
        assertEquals("high", dto.motionActivity?.confidence)
        assertEquals(24.0, dto.motionActivity?.durationsSec?.get("automotive") ?: 0.0, 1e-9)

        assertNotNull(dto.activityContext)
        assertEquals("automotive", dto.activityContext?.dominant)
        assertEquals("high", dto.activityContext?.bestConfidence)
        assertEquals(0.90, dto.activityContext?.automotiveShare ?: 0.0, 1e-9)
        assertEquals(true, dto.activityContext?.isAutomotiveNow)
        assertEquals("2026-04-06T10:15:00Z", dto.activityContext?.windowStartedAt)
        assertEquals("2026-04-06T10:15:30Z", dto.activityContext?.windowEndedAt)

        assertNotNull(dto.pedometer)
        assertEquals(42, dto.pedometer?.steps)
        assertEquals(31.5, dto.pedometer?.distanceM ?: 0.0, 1e-9)
        assertEquals(1.8, dto.pedometer?.cadence ?: 0.0, 1e-9)
        assertEquals(0.56, dto.pedometer?.pace ?: 0.0, 1e-9)

        assertNotNull(dto.altimeter)
        assertEquals(-1.2, dto.altimeter?.relAltMMin ?: 0.0, 1e-9)
        assertEquals(3.4, dto.altimeter?.relAltMMax ?: 0.0, 1e-9)
        assertEquals(99.8, dto.altimeter?.pressureKpaMin ?: 0.0, 1e-9)
        assertEquals(100.3, dto.altimeter?.pressureKpaMax ?: 0.0, 1e-9)

        assertNotNull(dto.screenInteractionContext)
        assertEquals(3, dto.screenInteractionContext?.count)
        assertEquals(true, dto.screenInteractionContext?.recent)
        assertEquals(12.5, dto.screenInteractionContext?.activeSec ?: 0.0, 1e-9)
        assertEquals("2026-04-06T10:15:25Z", dto.screenInteractionContext?.lastAt)

        assertNotNull(dto.tripConfig)
        assertEquals(0.18, dto.tripConfig?.v2?.accelSharpG ?: 0.0, 1e-9)
        assertEquals(0.75, dto.tripConfig?.v2?.roadHighAbsG ?: 0.0, 1e-9)
    }

    @Test
    fun map_uses_activity_fallback_when_only_legacy_activity_sample_exists() {
        val batch = TelemetryBatch(
            deviceId = "device-1",
            driverId = null,
            sessionId = "session-1",
            createdAt = Instant.parse("2026-04-06T10:15:30Z"),
            trackingMode = TrackingMode.SINGLE_TRIP,
            transportMode = "car",
            batchId = "batch-2",
            batchSeq = 8,
            frames = emptyList(),
            events = emptyList(),
            activitySummary = com.alex.android_telemetry.telemetry.domain.model.ActivitySample(
                timestamp = Instant.parse("2026-04-06T10:15:30Z"),
                dominant = "walking",
                confidence = "medium",
            ),
        )

        val dto = mapper.map(batch)

        assertNotNull(dto.motionActivity)
        assertEquals("walking", dto.motionActivity?.dominant)
        assertEquals("medium", dto.motionActivity?.confidence)
        assertEquals(1.0, dto.motionActivity?.durationsSec?.get("walking") ?: 0.0, 1e-9)

        assertNotNull(dto.activityContext)
        assertEquals("walking", dto.activityContext?.dominant)
        assertEquals("medium", dto.activityContext?.bestConfidence)
        assertEquals(1.0, dto.activityContext?.walkingShare ?: 0.0, 1e-9)
        assertEquals(false, dto.activityContext?.isAutomotiveNow)

        assertNull(dto.pedometer)
        assertNull(dto.altimeter)
        assertNull(dto.screenInteractionContext)
    }

    @Test
    fun map_serializes_canonical_wire_keys() {
        val dto = mapper.map(contractBatch())
        val encoded = json.encodeToString(TelemetryBatchDto.serializer(), dto)

        val requiredKeys = listOf(
            "device_id",
            "driver_id",
            "session_id",
            "timestamp",
            "tracking_mode",
            "transport_mode",
            "batch_id",
            "batch_seq",
            "samples",
            "events",
            "trip_config",
            "speed_m_s",
            "a_long_g",
            "a_lat_g",
            "a_vert_g",
            "accel_in_turn",
            "algo_version"
        )

        val missingKeys = requiredKeys.filter { key ->
            !encoded.contains("\"$key\"")
        }



        assertTrue(
            "Missing canonical keys: $missingKeys",
            missingKeys.isEmpty()
        )

        assertNotNull(dto.appVersion)
        assertNotNull(dto.appBuild)

        assertNotNull(dto.locale)
        assertNotNull(dto.timezone)

        listOf(
            "deviceId",
            "driverId",
            "sessionId",
            "batchId",
            "batchSeq",
            "createdAt",
            "speedMS",
            "aLongG",
            "aLatG",
            "aVertG"
        ).forEach { key ->
            assertFalse("CamelCase key leaked: $key in $encoded", encoded.contains("\"$key\""))
        }

        assertTrue(encoded.contains("\"speed_m_s\""))
        assertTrue(encoded.contains("\"a_long_g\""))
        assertTrue(encoded.contains("\"a_lat_g\""))
        assertTrue(encoded.contains("\"a_vert_g\""))
        assertTrue(encoded.contains("\"accel_in_turn\""))
        assertTrue(encoded.contains("\"class\""))
        assertTrue(encoded.contains("\"algo_version\""))
    }

    @Test
    fun map_sanitizes_nan_and_infinity() {
        val batch = TelemetryBatch(
            deviceId = "device-1",
            driverId = "driver-1",
            sessionId = "session-1",
            createdAt = Instant.parse("2026-04-30T12:00:00Z"),
            trackingMode = TrackingMode.SINGLE_TRIP,
            transportMode = "car",
            batchId = "batch-nan",
            batchSeq = 1,
            frames = listOf(
                TelemetryFrame(
                    timestamp = Instant.parse("2026-04-30T12:00:00Z"),
                    location = LocationFix(
                        timestamp = Instant.parse("2026-04-30T12:00:00Z"),
                        lat = 55.0,
                        lon = 37.0,
                        horizontalAccuracyM = Double.NaN,
                        verticalAccuracyM = Double.POSITIVE_INFINITY,
                        speedMS = Double.NEGATIVE_INFINITY,
                        speedAccuracyMS = Double.NaN,
                        bearingDeg = 90.0,
                        bearingAccuracyDeg = Double.POSITIVE_INFINITY,
                    ),
                    imu = ImuSample(
                        timestamp = Instant.parse("2026-04-30T12:00:00Z"),
                        accelX = Double.NaN,
                        accelY = 0.2,
                        accelZ = Double.POSITIVE_INFINITY,
                        gyroX = 0.01,
                        gyroY = Double.NEGATIVE_INFINITY,
                        gyroZ = 0.03,
                    ),
                    attitude = Attitude(
                        yaw = Double.NaN,
                        pitch = 0.1,
                        roll = Double.POSITIVE_INFINITY,
                    ),
                    motionVector = MotionVector(
                        aLongG = Double.NaN,
                        aLatG = 0.4,
                        aVertG = Double.NEGATIVE_INFINITY,
                    ),
                )
            ),
            events = listOf(
                DetectedTelemetryEvent(
                    type = TelemetryEventType.ACCEL,
                    timestamp = Instant.parse("2026-04-30T12:00:00Z"),
                    intensity = Double.NaN,
                    speedMS = Double.POSITIVE_INFINITY,
                    eventClass = "sharp",
                )
            ),
        )

        val dto = mapper.map(batch)
        val sample = dto.samples.single()
        val event = dto.events.single()

        assertEquals(0.0, sample.hAcc ?: -1.0, 1e-9)
        assertEquals(0.0, sample.vAcc ?: -1.0, 1e-9)
        assertEquals(0.0, sample.speedMS ?: -1.0, 1e-9)
        assertEquals(0.0, sample.speedAcc ?: -1.0, 1e-9)
        assertEquals(0.0, sample.courseAcc ?: -1.0, 1e-9)

        assertEquals(0.0, sample.accel?.x ?: -1.0, 1e-9)
        assertEquals(0.0, sample.accel?.z ?: -1.0, 1e-9)

        assertEquals(0.0, sample.rotation?.y ?: -1.0, 1e-9)

        assertEquals(0.0, sample.attitude?.yaw ?: -1.0, 1e-9)
        assertEquals(0.0, sample.attitude?.roll ?: -1.0, 1e-9)

        assertEquals(0.0, sample.aLongG ?: -1.0, 1e-9)
        assertEquals(0.0, sample.aVertG ?: -1.0, 1e-9)

        assertEquals(0.0, event.speedMS ?: -1.0, 1e-9)

        val encoded = json.encodeToString(TelemetryBatchDto.serializer(), dto)
        assertFalse(encoded.contains("NaN"))
        assertFalse(encoded.contains("Infinity"))
    }

    @Test
    fun map_preserves_iso8601_utc_timestamps_and_batch_seq() {
        val dto = mapper.map(contractBatch())

        assertEquals("2026-04-30T12:00:00Z", dto.timestamp)
        assertEquals("2026-04-30T12:00:00.100Z", dto.samples.single().t)
        assertEquals("2026-04-30T12:00:00.200Z", dto.events.single().t)
        assertEquals(42, dto.batchSeq)
    }

    @Test
    fun map_serializes_canonical_v2_event_types() {
        val dto = mapper.map(
            contractBatch(
                events = listOf(
                    event(TelemetryEventType.ACCEL, "sharp"),
                    event(TelemetryEventType.BRAKE, "emergency"),
                    event(TelemetryEventType.TURN, "sharp"),
                    event(TelemetryEventType.ACCEL_IN_TURN, "sharp"),
                    event(TelemetryEventType.BRAKE_IN_TURN, "emergency"),
                    DetectedTelemetryEvent(
                        type = TelemetryEventType.ROAD_ANOMALY,
                        timestamp = Instant.parse("2026-04-30T12:00:00.200Z"),
                        intensity = 0.72,
                        speedMS = 12.0,
                        subtype = "bump",
                        severity = "low",
                        origin = "client",
                        algoVersion = "v2",
                    ),
                )
            )
        )

        assertEquals(
            listOf(
                "accel",
                "brake",
                "turn",
                "accel_in_turn",
                "brake_in_turn",
                "road_anomaly",
            ),
            dto.events.map { it.type },
        )

        assertFalse(dto.events.map { it.type }.contains("combined"))
        assertEquals("low", dto.events.last().severity)
        assertEquals("bump", dto.events.last().subtype)
        assertEquals("v2", dto.events.last().algoVersion)
    }

    private fun contractBatch(
        events: List<DetectedTelemetryEvent> = listOf(
            DetectedTelemetryEvent(
                type = TelemetryEventType.ACCEL_IN_TURN,
                timestamp = Instant.parse("2026-04-30T12:00:00.200Z"),
                intensity = 0.24,
                speedMS = 14.5,
                eventClass = "sharp",
                origin = "client",
                algoVersion = "v2",
                meta = mapOf("source" to "golden"),
            )
        )
    ): TelemetryBatch {
        return TelemetryBatch(
            deviceId = "device-1",
            driverId = "driver-1",
            sessionId = "session-1",
            createdAt = Instant.parse("2026-04-30T12:00:00Z"),
            trackingMode = TrackingMode.DAY_MONITORING,
            transportMode = "car",
            batchId = "batch-1",
            batchSeq = 42,
            frames = listOf(
                TelemetryFrame(
                    timestamp = Instant.parse("2026-04-30T12:00:00.100Z"),
                    location = LocationFix(
                        timestamp = Instant.parse("2026-04-30T12:00:00.100Z"),
                        lat = 55.7558,
                        lon = 37.6173,
                        horizontalAccuracyM = 4.5,
                        verticalAccuracyM = 8.0,
                        speedMS = 14.5,
                        speedAccuracyMS = 0.6,
                        bearingDeg = 90.0,
                        bearingAccuracyDeg = 2.0,
                    ),
                    imu = ImuSample(
                        timestamp = Instant.parse("2026-04-30T12:00:00.100Z"),
                        accelX = 0.1,
                        accelY = 0.2,
                        accelZ = 0.98,
                        gyroX = 0.01,
                        gyroY = 0.02,
                        gyroZ = 0.03,
                    ),
                    attitude = Attitude(
                        yaw = 0.1,
                        pitch = 0.2,
                        roll = 0.3,
                    ),
                    motionVector = MotionVector(
                        aLongG = 0.24,
                        aLatG = 0.40,
                        aVertG = 0.02,
                    ),
                )
            ),
            events = events,
            tripConfig = EventThresholdSet(
                accelSharpG = 0.18,
                accelEmergencyG = 0.28,
                brakeSharpG = 0.22,
                brakeEmergencyG = 0.32,
                turnSharpG = 0.22,
                turnEmergencyG = 0.30,
                roadLowG = 0.45,
                roadHighG = 0.75,
                minSpeedForAccelBrakeMS = 3.0,
                minSpeedForTurnMS = 5.0,
                accelBrakeCooldownS = 1.2,
                turnCooldownS = 0.8,
                roadCooldownS = 1.0,
                roadWindowS = 0.4,
                combinedLatMinG = 0.35,
                accelInTurnSharpG = 0.18,
                accelInTurnEmergencyG = 0.28,
                brakeInTurnSharpG = 0.22,
                brakeInTurnEmergencyG = 0.32,
                combinedCooldownS = 0.8,
            ),
        )
    }

    private fun event(
        type: TelemetryEventType,
        eventClass: String,
    ): DetectedTelemetryEvent {
        return DetectedTelemetryEvent(
            type = type,
            timestamp = Instant.parse("2026-04-30T12:00:00.200Z"),
            intensity = 0.24,
            speedMS = 14.5,
            eventClass = eventClass,
            origin = "client",
            algoVersion = "v2",
        )
    }
}