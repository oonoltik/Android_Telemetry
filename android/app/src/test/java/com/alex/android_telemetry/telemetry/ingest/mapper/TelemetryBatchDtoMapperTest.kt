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

class TelemetryBatchDtoMapperTest {

    private val mapper = TelemetryBatchDtoMapper()

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
                batteryLevel = 0.72,
                batteryState = "charging",
                lowPowerMode = false,
            ),
            networkState = NetworkStateSnapshot(
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
}