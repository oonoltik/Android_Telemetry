package com.alex.android_telemetry.telemetry.detectors

import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import java.time.Duration
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.max

class MotionVectorComputer {
    fun compute(imu: ImuSample?, location: LocationFix?): MotionVector {
        if (imu == null && location == null) return MotionVector(speedMS = location?.speedMS)
        return MotionVector(
            aLongG = imu?.accelY,
            aLatG = imu?.accelX,
            aVertG = imu?.accelZ,
            yawRate = imu?.gyroZ,
            speedMS = location?.speedMS,
        )
    }
}

interface TelemetryEventDetector {
    fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent?
}

class AccelEventDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private var lastEventAt: Instant? = null

    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val speed = vector.speedMS ?: return null
        val aLong = vector.aLongG ?: return null
        if (speed < cfg.minSpeedForAccelBrakeMS || aLong < cfg.accelSharpG) return null
        if (!cooldownPassed(lastEventAt, now, cfg.accelBrakeCooldownS)) return null
        lastEventAt = now
        return DetectedTelemetryEvent(
            type = TelemetryEventType.ACCEL,
            timestamp = now,
            intensity = aLong,
            speedMS = speed,
            eventClass = if (aLong >= cfg.accelEmergencyG) "emergency" else "sharp",
            algoVersion = "android-v1",
            details = "longitudinal acceleration threshold crossed",
        )
    }
}

class BrakeEventDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private var lastEventAt: Instant? = null

    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val speed = vector.speedMS ?: return null
        val aLong = vector.aLongG ?: return null
        val intensity = abs(aLong)
        if (speed < cfg.minSpeedForAccelBrakeMS || aLong > -cfg.brakeSharpG) return null
        if (!cooldownPassed(lastEventAt, now, cfg.accelBrakeCooldownS)) return null
        lastEventAt = now
        return DetectedTelemetryEvent(
            type = TelemetryEventType.BRAKE,
            timestamp = now,
            intensity = intensity,
            speedMS = speed,
            eventClass = if (intensity >= cfg.brakeEmergencyG) "emergency" else "sharp",
            algoVersion = "android-v1",
            details = "longitudinal brake threshold crossed",
        )
    }
}

class TurnEventDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private var lastEventAt: Instant? = null

    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val speed = vector.speedMS ?: return null
        val aLat = vector.aLatG ?: return null
        if (speed < cfg.minSpeedForTurnMS || max(aLat, -aLat) < cfg.turnSharpG) return null
        if (!cooldownPassed(lastEventAt, now, cfg.turnCooldownS)) return null
        lastEventAt = now
        return DetectedTelemetryEvent(
            type = TelemetryEventType.TURN,
            timestamp = now,
            intensity = abs(aLat),
            speedMS = speed,
            eventClass = if (abs(aLat) >= cfg.turnEmergencyG) "emergency" else "sharp",
            subtype = if (aLat > 0) "left_or_right_positive_lat" else "left_or_right_negative_lat",
            algoVersion = "android-v1",
        )
    }
}

class RoadAnomalyDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private var lastEventAt: Instant? = null

    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val aVert = abs(vector.aVertG ?: return null)
        val low = cfg.roadLowG ?: return null
        if (aVert < low) return null
        if (!cooldownPassed(lastEventAt, now, cfg.roadCooldownS)) return null
        lastEventAt = now
        val high = cfg.roadHighG
        return DetectedTelemetryEvent(
            type = TelemetryEventType.ROAD_ANOMALY,
            timestamp = now,
            intensity = aVert,
            speedMS = vector.speedMS,
            severity = if (high != null && aVert >= high) "high" else "low",
            algoVersion = "android-v1",
        )
    }
}

private fun cooldownPassed(lastAt: Instant?, now: Instant, seconds: Double): Boolean {
    if (lastAt == null) return true
    val gap = (now - lastAt).inWholeMilliseconds
    return gap >= (seconds * 1000.0).toLong()
}
