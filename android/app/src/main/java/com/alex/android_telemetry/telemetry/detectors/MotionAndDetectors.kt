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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class MotionVectorComputer {

    fun compute(imu: ImuSample?, location: LocationFix?): MotionVector {
        if (imu == null && location == null) {
            return MotionVector(speedMS = location?.speedMS)
        }

        return computeProjected(
            accelRefNorthG = imu?.accelX,
            accelRefEastG = imu?.accelY,
            accelRefUpG = imu?.accelZ,
            speedMS = location?.speedMS,
            courseRad = null,
            imuForwardAxisRefNorth = null,
            imuForwardAxisRefEast = null,
            preferGpsProjection = false,
        )
    }

    fun computeProjected(
        accelRefNorthG: Double?,
        accelRefEastG: Double?,
        accelRefUpG: Double?,
        speedMS: Double?,
        courseRad: Double? = null,
        imuForwardAxisRefNorth: Double? = null,
        imuForwardAxisRefEast: Double? = null,
        preferGpsProjection: Boolean = false,
    ): MotionVector {
        val aNorth = accelRefNorthG
        val aEast = accelRefEastG
        val aUp = accelRefUpG

        if (aNorth == null && aEast == null && aUp == null) {
            return MotionVector(
                aLongG = null,
                aLatG = null,
                aVertG = null,
                yawRate = null,
                speedMS = speedMS,
            )
        }

        val gpsProjection = if (preferGpsProjection && courseRad != null && aNorth != null && aEast != null) {
            projectWithCourse(
                aNorth = aNorth,
                aEast = aEast,
                courseRad = courseRad,
            )
        } else {
            null
        }

        val imuProjection = if (aNorth != null && aEast != null) {
            val axis = normalizeAxisOrNull(
                north = imuForwardAxisRefNorth,
                east = imuForwardAxisRefEast,
            )

            if (axis != null) {
                projectWithAxis(
                    aNorth = aNorth,
                    aEast = aEast,
                    axisNorth = axis.first,
                    axisEast = axis.second,
                )
            } else {
                projectFallback(
                    aNorth = aNorth,
                    aEast = aEast,
                )
            }
        } else {
            null
        }

        return MotionVector(
            aLongG = gpsProjection?.first ?: imuProjection?.first,
            aLatG = gpsProjection?.second ?: imuProjection?.second,
            aVertG = aUp,
            yawRate = null,
            speedMS = speedMS,
        )
    }

    private fun projectWithCourse(
        aNorth: Double,
        aEast: Double,
        courseRad: Double,
    ): Pair<Double, Double> {
        val vHatNorth = cos(courseRad)
        val vHatEast = sin(courseRad)

        val vPerpNorth = -sin(courseRad)
        val vPerpEast = cos(courseRad)

        val aLong = (aNorth * vHatNorth) + (aEast * vHatEast)
        val aLat = (aNorth * vPerpNorth) + (aEast * vPerpEast)
        return aLong to aLat
    }

    private fun projectWithAxis(
        aNorth: Double,
        aEast: Double,
        axisNorth: Double,
        axisEast: Double,
    ): Pair<Double, Double> {
        val perpNorth = -axisEast
        val perpEast = axisNorth

        val aLong = (aNorth * axisNorth) + (aEast * axisEast)
        val aLat = (aNorth * perpNorth) + (aEast * perpEast)
        return aLong to aLat
    }

    private fun projectFallback(
        aNorth: Double,
        aEast: Double,
    ): Pair<Double, Double> {
        return aEast to aNorth
    }

    private fun normalizeAxisOrNull(
        north: Double?,
        east: Double?,
    ): Pair<Double, Double>? {
        if (north == null || east == null) return null
        val norm = sqrt((north * north) + (east * east))
        if (norm <= 1e-9) return null
        return (north / norm) to (east / norm)
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