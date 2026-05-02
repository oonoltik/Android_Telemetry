package com.alex.android_telemetry.telemetry.detectors

import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// MotionVectorComputer
//
// Порт iOS SensorManager V2:
//   1. Rotation matrix → device frame → reference frame (North/East/Up)
//   2. GPS-проекция (приоритет при надёжном курсе)
//   3. PCA IMU-калибровка forward axis + знаковая коррекция через dSpeed
//   4. Fallback: aLong=aEast, aLat=aNorth
// ─────────────────────────────────────────────────────────────────────────────

class MotionVectorComputer {

    companion object {
        private const val CALIB_MIN_HORIZ_G = 0.04
        private const val CALIB_MAX_YAW_RATE = 0.6
        private const val CALIB_UPDATE_EVERY = 5
        private const val CALIB_MIN_SAMPLES = 40
        private const val CALIB_TARGET_SECONDS = 5.0
        private const val PHONE_MOVE_ANGLE_RAD = 0.35
        private const val PHONE_MOVE_SUPPRESS_SEC = 3.0
        private const val GPS_COURSE_MAX_AGE_SEC = 10.0
        private const val GPS_MIN_SPEED_FOR_COURSE = 3.0
        private const val GPS_MAX_COURSE_ACCURACY_DEG = 15.0
        private const val IMU_AXIS_SEED_ALPHA = 0.1
    }

    // Rotation matrix row-major, device → reference (North/East/Up)
    // Заполни из android.hardware.SensorManager.getRotationMatrix()
    data class RotationMatrix(
        val m11: Double, val m12: Double, val m13: Double,
        val m21: Double, val m22: Double, val m23: Double,
        val m31: Double, val m32: Double, val m33: Double,
    )

    data class SensorFrame(
        val accelDevX: Double, val accelDevY: Double, val accelDevZ: Double,
        val gyroZ: Double,
        val gravityDevX: Double, val gravityDevY: Double, val gravityDevZ: Double,
        val rotationMatrix: RotationMatrix,
        val speedMS: Double?,
        val courseRad: Double?,
        val courseAccuracyDeg: Double?,
        val courseAgeMs: Long,
        val nowMs: Long,
    )

    data class ProjectedAccelerations(
        val aLongG: Double?,
        val aLatG: Double?,
        val aVertG: Double?,
        val suppressed: Boolean,
    )

    private enum class CalibState { NONE, CALIBRATING, READY }

    private var calibState = CalibState.NONE
    private var calibStartedMs: Long? = null
    private var covXX = 0.0; private var covXY = 0.0; private var covYY = 0.0
    private var covN = 0
    private var forwardAxisNorth: Double? = null
    private var forwardAxisEast: Double? = null
    private var signScore = 0.0
    private var lastSpeedForSign: Double? = null
    private var suppressUntilMs: Long = 0
    private var lastGravNorm: Triple<Double, Double, Double>? = null

    /** Полный update с rotation matrix (новый путь) */
    fun update(frame: SensorFrame): ProjectedAccelerations {
        val suppressed = frame.nowMs < suppressUntilMs

        if (detectPhoneMoved(frame)) {
            suppressUntilMs = frame.nowMs + (PHONE_MOVE_SUPPRESS_SEC * 1000).toLong()
            resetCalibration()
        }

        val (aNorth, aEast, aUp) = deviceToReference(
            frame.accelDevX, frame.accelDevY, frame.accelDevZ, frame.rotationMatrix
        )

        val courseRad = if (frame.courseAgeMs <= GPS_COURSE_MAX_AGE_SEC * 1000)
            frame.courseRad else null
        val speedOk = (frame.speedMS ?: -1.0) >= GPS_MIN_SPEED_FOR_COURSE

        if (courseRad != null && speedOk) {
            val vN = cos(courseRad); val vE = sin(courseRad)
            val curN = forwardAxisNorth; val curE = forwardAxisEast
            if (curN != null && curE != null) {
                val newN = curN * (1.0 - IMU_AXIS_SEED_ALPHA) + vN * IMU_AXIS_SEED_ALPHA
                val newE = curE * (1.0 - IMU_AXIS_SEED_ALPHA) + vE * IMU_AXIS_SEED_ALPHA
                val norm = sqrt(newN * newN + newE * newE)
                if (norm > 1e-9) { forwardAxisNorth = newN / norm; forwardAxisEast = newE / norm }
            } else {
                forwardAxisNorth = vN; forwardAxisEast = vE
                signScore = 1.0; calibState = CalibState.READY
            }
        }

        if (!suppressed) updateCalibration(aNorth, aEast, frame.gyroZ, frame.speedMS, frame.nowMs)

        val gpsCourseTrusted = isCourseReliable(courseRad, frame)
        val axis = calibratedAxisWithSign()

        val (aLong, aLat) = when {
            gpsCourseTrusted && courseRad != null -> projectWithCourse(aNorth, aEast, courseRad)
            axis != null -> {
                val (axN, axE) = axis
                (aNorth * axN + aEast * axE) to (aNorth * -axE + aEast * axN)
            }
            else -> aEast to aNorth
        }

        return ProjectedAccelerations(aLongG = aLong, aLatG = aLat, aVertG = aUp, suppressed = suppressed)
    }

    /** Совместимость с существующим кодом — вызывается без rotation matrix */
    fun compute(imu: ImuSample?, location: LocationFix?): MotionVector {
        val courseRad = location?.bearingDeg?.takeIf { it.isFinite() }?.let { Math.toRadians(it) }
        val aN = imu?.accelX; val aE = imu?.accelY; val aU = imu?.accelZ

        // PCA-калибровка: обновляем forward axis на каждом тике
        if (aN != null && aE != null) {
            val gyroZ = imu?.gyroZ ?: 0.0
            updateCalibration(aN, aE, gyroZ, location?.speedMS, System.currentTimeMillis())
        }

        // GPS seed: если GPS надёжен — сразу сеем ось
        val speedMS = location?.speedMS
        if (courseRad != null && (speedMS ?: -1.0) >= GPS_MIN_SPEED_FOR_COURSE) {
            val vN = cos(courseRad); val vE = sin(courseRad)
            val curN = forwardAxisNorth; val curE = forwardAxisEast
            if (curN != null && curE != null) {
                val newN = curN * (1.0 - IMU_AXIS_SEED_ALPHA) + vN * IMU_AXIS_SEED_ALPHA
                val newE = curE * (1.0 - IMU_AXIS_SEED_ALPHA) + vE * IMU_AXIS_SEED_ALPHA
                val norm = sqrt(newN * newN + newE * newE)
                if (norm > 1e-9) { forwardAxisNorth = newN / norm; forwardAxisEast = newE / norm }
            } else {
                forwardAxisNorth = vN; forwardAxisEast = vE
                signScore = 1.0; calibState = CalibState.READY
            }
        }

        val (aLong, aLat) = when {
            courseRad != null && aN != null && aE != null -> projectWithCourse(aN, aE, courseRad)
            aN != null && aE != null -> {
                val axis = calibratedAxisWithSign()
                if (axis != null) {
                    val (axN, axE) = axis
                    (aN * axN + aE * axE) to (aN * -axE + aE * axN)
                } else {
                    aE to aN
                }
            }
            else -> null to null
        }

        return MotionVector(aLongG = aLong, aLatG = aLat, aVertG = aU, yawRate = null, speedMS = location?.speedMS)
    }

    fun resetCalibration() {
        covXX = 0.0; covXY = 0.0; covYY = 0.0; covN = 0
        forwardAxisNorth = null; forwardAxisEast = null
        calibState = CalibState.NONE; calibStartedMs = null
        signScore = 0.0; lastSpeedForSign = null
    }

    private fun deviceToReference(dX: Double, dY: Double, dZ: Double, m: RotationMatrix): Triple<Double, Double, Double> =
        Triple(
            m.m11 * dX + m.m21 * dY + m.m31 * dZ,
            m.m12 * dX + m.m22 * dY + m.m32 * dZ,
            m.m13 * dX + m.m23 * dY + m.m33 * dZ,
        )

    private fun projectWithCourse(aN: Double, aE: Double, cr: Double): Pair<Double, Double> =
        (aN * cos(cr) + aE * sin(cr)) to (aN * -sin(cr) + aE * cos(cr))

    private fun isCourseReliable(courseRad: Double?, frame: SensorFrame): Boolean {
        if (courseRad == null) return false
        if ((frame.speedMS ?: -1.0) < GPS_MIN_SPEED_FOR_COURSE) return false
        val acc = frame.courseAccuracyDeg
        if (acc != null && acc >= 0 && acc > GPS_MAX_COURSE_ACCURACY_DEG) return false
        return true
    }

    private fun updateCalibration(aN: Double, aE: Double, gyroZ: Double, speedMS: Double?, nowMs: Long) {
        val mag = sqrt(aN * aN + aE * aE)
        if (mag < CALIB_MIN_HORIZ_G || abs(gyroZ) > CALIB_MAX_YAW_RATE) return
        if (calibState == CalibState.NONE) { calibState = CalibState.CALIBRATING; calibStartedMs = nowMs }
        covXX += aN * aN; covXY += aN * aE; covYY += aE * aE; covN++
        if (covN % CALIB_UPDATE_EVERY == 0) {
            val v = principalEigenvector(covXX, covXY, covYY) ?: return
            forwardAxisNorth = v.first; forwardAxisEast = v.second
            if (speedMS != null) {
                lastSpeedForSign?.let { signScore += (speedMS - it) * (aN * v.first + aE * v.second) }
                lastSpeedForSign = speedMS
            }
            if (calibState != CalibState.READY) {
                val elapsed = (nowMs - (calibStartedMs ?: nowMs)) / 1000.0
                if (elapsed >= CALIB_TARGET_SECONDS && covN >= CALIB_MIN_SAMPLES) calibState = CalibState.READY
                else if (covN >= CALIB_MIN_SAMPLES * 3) calibState = CalibState.READY
            }
        }
    }

    private fun principalEigenvector(xx: Double, xy: Double, yy: Double): Pair<Double, Double>? {
        val trace = xx + yy
        val disc = max(0.0, trace * trace - 4.0 * (xx * yy - xy * xy))
        val lambda1 = 0.5 * (trace + sqrt(disc))
        val a = xx - lambda1
        val (vx, vy) = if (abs(xy) > 1e-9) 1.0 to (-a / xy) else if (xx >= yy) 1.0 to 0.0 else 0.0 to 1.0
        val n = sqrt(vx * vx + vy * vy)
        if (n <= 1e-9) return null
        return (vx / n) to (vy / n)
    }

    private fun calibratedAxisWithSign(): Pair<Double, Double>? {
        val n = forwardAxisNorth ?: return null
        val e = forwardAxisEast ?: return null
        return if (signScore < 0) (-n to -e) else (n to e)
    }

    private fun detectPhoneMoved(frame: SensorFrame): Boolean {
        val gx = frame.gravityDevX; val gy = frame.gravityDevY; val gz = frame.gravityDevZ
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        if (n < 1e-9) return false
        val norm = Triple(gx / n, gy / n, gz / n)
        val prev = lastGravNorm
        lastGravNorm = norm
        if (prev == null) return false
        val dot = prev.first * norm.first + prev.second * norm.second + prev.third * norm.third
        return acos(max(-1.0, min(1.0, dot))) >= PHONE_MOVE_ANGLE_RAD
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Event detectors
// ─────────────────────────────────────────────────────────────────────────────

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
            type = TelemetryEventType.ACCEL, timestamp = now, intensity = aLong, speedMS = speed,
            eventClass = if (aLong >= cfg.accelEmergencyG) "emergency" else "sharp",
            algoVersion = "v2", details = "longitudinal acceleration threshold crossed",
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
            type = TelemetryEventType.BRAKE, timestamp = now, intensity = intensity, speedMS = speed,
            eventClass = if (intensity >= cfg.brakeEmergencyG) "emergency" else "sharp",
            algoVersion = "v2", details = "longitudinal brake threshold crossed",
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
        val absLat = abs(aLat)
        if (speed < cfg.minSpeedForTurnMS || absLat < cfg.turnSharpG) return null
        if (!cooldownPassed(lastEventAt, now, cfg.turnCooldownS)) return null
        lastEventAt = now
        return DetectedTelemetryEvent(
            type = TelemetryEventType.TURN, timestamp = now, intensity = absLat, speedMS = speed,
            eventClass = if (absLat >= cfg.turnEmergencyG) "emergency" else "sharp",
            subtype = if (aLat > 0) "right" else "left",  // iOS: aLat > 0 = right
            algoVersion = "v2",
        )
    }
}

class AccelInTurnDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private var lastEventAt: Instant? = null
    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val speed = vector.speedMS ?: return null
        val aLong = vector.aLongG ?: return null
        val aLat = vector.aLatG ?: return null
        if (speed < cfg.minSpeedForTurnMS) return null
        if (abs(aLat) < cfg.combinedLatMinG) return null
        val sharpThreshold = cfg.accelInTurnSharpG ?: cfg.accelSharpG
        val emergencyThreshold = cfg.accelInTurnEmergencyG ?: cfg.accelEmergencyG
        if (aLong < sharpThreshold) return null
        if (!cooldownPassed(lastEventAt, now, cfg.accelBrakeCooldownS)) return null
        lastEventAt = now
        return DetectedTelemetryEvent(
            type = TelemetryEventType.ACCEL_IN_TURN, timestamp = now, intensity = aLong, speedMS = speed,
            eventClass = if (aLong >= emergencyThreshold) "emergency" else "sharp",
            algoVersion = "v2",
        )
    }
}

class BrakeInTurnDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private var lastEventAt: Instant? = null
    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val speed = vector.speedMS ?: return null
        val aLong = vector.aLongG ?: return null
        val aLat = vector.aLatG ?: return null
        if (speed < cfg.minSpeedForTurnMS) return null
        if (abs(aLat) < cfg.combinedLatMinG) return null
        val sharpThreshold = cfg.brakeInTurnSharpG ?: cfg.brakeSharpG
        val emergencyThreshold = cfg.brakeInTurnEmergencyG ?: cfg.brakeEmergencyG
        if (aLong > -sharpThreshold) return null
        if (!cooldownPassed(lastEventAt, now, cfg.accelBrakeCooldownS)) return null
        lastEventAt = now
        return DetectedTelemetryEvent(
            type = TelemetryEventType.BRAKE_IN_TURN, timestamp = now, intensity = abs(aLong), speedMS = speed,
            eventClass = if (abs(aLong) >= emergencyThreshold) "emergency" else "sharp",
            algoVersion = "v2",
        )
    }
}

class RoadAnomalyDetector(
    private val thresholds: () -> EventThresholdSet,
) : TelemetryEventDetector {
    private data class VertPoint(val timestampMs: Long, val aVertG: Double)
    private val vertBuffer = ArrayDeque<VertPoint>()
    private var lastEventAt: Instant? = null

    override fun detect(vector: MotionVector, now: Instant): DetectedTelemetryEvent? =
        detectWithTimestamp(vector, now, now.toEpochMilliseconds())

    fun detectWithTimestamp(vector: MotionVector, now: Instant, nowMs: Long): DetectedTelemetryEvent? {
        val cfg = thresholds()
        val aVert = vector.aVertG ?: return null
        val speed = vector.speedMS ?: -1.0
        if (speed in 0.0..2.0) return null

        vertBuffer.addLast(VertPoint(nowMs, aVert))
        val windowMs = (cfg.roadWindowS * 1000).toLong()
        while (vertBuffer.isNotEmpty() && vertBuffer.first().timestampMs < nowMs - windowMs) {
            vertBuffer.removeFirst()
        }
        if (vertBuffer.size < 3) return null
        if (!cooldownPassed(lastEventAt, now, cfg.roadCooldownS)) return null

        var maxAbs = 0.0; var maxVal = Double.NEGATIVE_INFINITY; var minVal = Double.POSITIVE_INFINITY
        for (p in vertBuffer) {
            val v = p.aVertG
            if (abs(v) > maxAbs) maxAbs = abs(v)
            if (v > maxVal) maxVal = v
            if (v < minVal) minVal = v
        }

        val p2p = maxVal - minVal
        val severity = when {
            p2p >= (cfg.roadHighG ?: 1.10) || maxAbs >= 0.75 -> "high"
            p2p >= (cfg.roadLowG ?: 0.70) || maxAbs >= 0.45 -> "low"
            else -> return null
        }

        lastEventAt = now
        val subtype = when {
            minVal <= -0.35 && maxVal >= 0.35 -> "pothole"
            (nowMs - (vertBuffer.firstOrNull()?.timestampMs ?: nowMs)) >= (windowMs * 0.85).toLong() -> "speed_bump"
            else -> "bump"
        }

        return DetectedTelemetryEvent(
            type = TelemetryEventType.ROAD_ANOMALY, timestamp = now,
            intensity = p2p, speedMS = if (speed >= 0) speed else null,
            severity = severity, subtype = subtype, algoVersion = "v2",
        )
    }

    fun clearBuffer() { vertBuffer.clear() }
}

private fun cooldownPassed(lastAt: Instant?, now: Instant, seconds: Double): Boolean {
    if (lastAt == null) return true
    return (now - lastAt).inWholeMilliseconds >= (seconds * 1000.0).toLong()
}