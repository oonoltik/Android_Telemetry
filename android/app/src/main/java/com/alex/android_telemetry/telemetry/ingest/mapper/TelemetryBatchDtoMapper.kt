package com.alex.android_telemetry.telemetry.ingest.mapper

import android.os.Build
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.telemetry.domain.model.ActivityContextSummary
import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSummary
import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.MotionActivitySummary
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.PedometerSummary
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionContextSummary
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.ingest.api.ActivityContextBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.AltimeterBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.AttitudeDto
import com.alex.android_telemetry.telemetry.ingest.api.Axis3Dto
import com.alex.android_telemetry.telemetry.ingest.api.ClassPenaltyDto
import com.alex.android_telemetry.telemetry.ingest.api.DeviceStateBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.HeadingBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.MotionActivityBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.NetworkBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.PedometerBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.PenaltyConfigDto
import com.alex.android_telemetry.telemetry.ingest.api.ScreenInteractionContextBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.ScoringConfigDto
import com.alex.android_telemetry.telemetry.ingest.api.SeverityPenaltyDto
import com.alex.android_telemetry.telemetry.ingest.api.SpeedFactorConfigDto
import com.alex.android_telemetry.telemetry.ingest.api.TelemetryBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.TelemetryEventDto
import com.alex.android_telemetry.telemetry.ingest.api.TelemetrySampleDto
import com.alex.android_telemetry.telemetry.ingest.api.TripConfigDto
import com.alex.android_telemetry.telemetry.ingest.api.V2ConfigDto
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import java.util.Locale
import java.util.TimeZone
import kotlinx.datetime.Instant

class TelemetryBatchDtoMapper {

    fun map(batch: TelemetryBatch): TelemetryBatchDto {
        val motionActivity = batch.motionActivitySummary
            ?.let(::mapMotionActivity)
            ?: batch.activitySummary?.let(::mapMotionActivityFallback)

        val activityContext = batch.activityContextSummary
            ?.let(::mapActivityContext)
            ?: batch.activitySummary?.let(::mapActivityContextFallback)

        return TelemetryBatchDto(
            deviceId = batch.deviceId,
            driverId = batch.driverId,
            sessionId = batch.sessionId,
            timestamp = batch.createdAt.toIsoString(),
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            iosVersion = Build.VERSION.RELEASE,
            deviceModel = Build.MODEL,
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
            trackingMode = batch.trackingMode?.toWireValue(),
            transportMode = batch.transportMode,
            batchId = batch.batchId,
            batchSeq = batch.batchSeq,
            samples = batch.frames.map(::mapFrame),
            events = batch.events.map(::mapEvent),
            tripConfig = batch.tripConfig?.let(::mapTripConfig),
            motionActivity = motionActivity,
            pedometer = batch.pedometerSummary?.let(::mapPedometer),
            altimeter = batch.altimeterSummary?.let(::mapAltimeter),
            deviceState = batch.deviceState?.let(::mapDeviceState),
            network = batch.networkState?.let(::mapNetwork),
            heading = batch.headingSummary?.let(::mapHeading),
            activityContext = activityContext,
            screenInteractionContext = batch.screenInteractionContextSummary?.let(::mapScreenInteractionContext),
        )
    }

    private fun mapFrame(frame: TelemetryFrame): TelemetrySampleDto {
        return TelemetrySampleDto(
            t = frame.timestamp.toIsoString(),
            lat = NumericSanitizer.sanitizeDouble(frame.location?.lat),
            lon = NumericSanitizer.sanitizeDouble(frame.location?.lon),
            hAcc = NumericSanitizer.sanitizeDouble(frame.location?.horizontalAccuracyM),
            vAcc = NumericSanitizer.sanitizeDouble(frame.location?.verticalAccuracyM),
            speedMS = NumericSanitizer.sanitizeDouble(frame.location?.speedMS),
            speedAcc = NumericSanitizer.sanitizeDouble(frame.location?.speedAccuracyMS),
            course = NumericSanitizer.sanitizeDouble(frame.location?.bearingDeg),
            courseAcc = NumericSanitizer.sanitizeDouble(frame.location?.bearingAccuracyDeg),
            accel = mapAxis3(
                x = frame.imu?.accelX,
                y = frame.imu?.accelY,
                z = frame.imu?.accelZ,
            ),
            rotation = mapAxis3(
                x = frame.imu?.gyroX,
                y = frame.imu?.gyroY,
                z = frame.imu?.gyroZ,
            ),
            attitude = if (
                frame.attitude?.yaw != null ||
                frame.attitude?.pitch != null ||
                frame.attitude?.roll != null
            ) {
                AttitudeDto(
                    yaw = NumericSanitizer.sanitizeDouble(frame.attitude?.yaw),
                    pitch = NumericSanitizer.sanitizeDouble(frame.attitude?.pitch),
                    roll = NumericSanitizer.sanitizeDouble(frame.attitude?.roll),
                )
            } else {
                null
            },
            aLongG = NumericSanitizer.sanitizeDouble(frame.motionVector?.aLongG),
            aLatG = NumericSanitizer.sanitizeDouble(frame.motionVector?.aLatG),
            aVertG = NumericSanitizer.sanitizeDouble(frame.motionVector?.aVertG),
        )
    }

    private fun mapAxis3(
        x: Double?,
        y: Double?,
        z: Double?,
    ): Axis3Dto? {
        val sx = NumericSanitizer.sanitizeDouble(x)
        val sy = NumericSanitizer.sanitizeDouble(y)
        val sz = NumericSanitizer.sanitizeDouble(z)
        if (sx == null && sy == null && sz == null) return null
        return Axis3Dto(x = sx, y = sy, z = sz)
    }

    private fun mapEvent(event: DetectedTelemetryEvent): TelemetryEventDto {
        return TelemetryEventDto(
            type = event.type.toWireValue(),
            t = event.timestamp.toIsoString(),
            intensity = NumericSanitizer.sanitizeDouble(event.intensity) ?: 0.0,
            details = event.details,
            origin = event.origin,
            algoVersion = event.algoVersion ?: "v2",
            speedMS = NumericSanitizer.sanitizeDouble(event.speedMS),
            eventClass = event.eventClass,
            subtype = event.subtype,
            severity = event.severity,
            metaJson = event.meta
                .takeIf { it.isNotEmpty() }
                ?.entries
                ?.joinToString(
                    prefix = "{",
                    postfix = "}",
                    separator = ",",
                ) { (k, v) ->
                    "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
                },
        )
    }

    private fun mapDeviceState(snapshot: DeviceStateSnapshot): DeviceStateBatchDto {
        return DeviceStateBatchDto(
            batteryLevel = NumericSanitizer.sanitizeDouble(snapshot.batteryLevel),
            batteryState = snapshot.batteryState,
            lowPowerMode = snapshot.lowPowerMode,
        )
    }

    private fun mapNetwork(snapshot: NetworkStateSnapshot): NetworkBatchDto {
        return NetworkBatchDto(
            status = snapshot.status,
            interfaceName = snapshot.interfaceType,
            expensive = snapshot.isExpensive,
            constrained = snapshot.isConstrained,
        )
    }

    private fun mapHeading(sample: HeadingSample): HeadingBatchDto {
        return HeadingBatchDto(
            magneticDeg = NumericSanitizer.sanitizeDouble(sample.magneticHeadingDeg),
            trueDeg = NumericSanitizer.sanitizeDouble(sample.trueHeadingDeg),
            accuracyDeg = NumericSanitizer.sanitizeDouble(sample.accuracyDeg),
        )
    }

    private fun mapMotionActivity(summary: MotionActivitySummary): MotionActivityBatchDto {
        return MotionActivityBatchDto(
            dominant = summary.dominant ?: "unknown",
            confidence = summary.confidence ?: "low",
            durationsSec = summary.durationsSec.mapValues { (_, value) ->
                NumericSanitizer.sanitizeDouble(value) ?: 0.0
            },
        )
    }

    private fun mapMotionActivityFallback(sample: ActivitySample): MotionActivityBatchDto {
        val dominant = sample.dominant ?: "unknown"
        return MotionActivityBatchDto(
            dominant = dominant,
            confidence = sample.confidence ?: "low",
            durationsSec = mapOf(dominant to 1.0),
        )
    }

    private fun mapActivityContext(summary: ActivityContextSummary): ActivityContextBatchDto {
        return ActivityContextBatchDto(
            dominant = summary.dominant,
            bestConfidence = summary.bestConfidence,
            stationaryShare = NumericSanitizer.sanitizeDouble(summary.stationaryShare),
            walkingShare = NumericSanitizer.sanitizeDouble(summary.walkingShare),
            runningShare = NumericSanitizer.sanitizeDouble(summary.runningShare),
            cyclingShare = NumericSanitizer.sanitizeDouble(summary.cyclingShare),
            automotiveShare = NumericSanitizer.sanitizeDouble(summary.automotiveShare),
            unknownShare = NumericSanitizer.sanitizeDouble(summary.unknownShare),
            nonAutomotiveStreakSec = NumericSanitizer.sanitizeDouble(summary.nonAutomotiveStreakSec),
            isAutomotiveNow = summary.isAutomotiveNow,
            windowStartedAt = summary.windowStartedAt?.toIsoString(),
            windowEndedAt = summary.windowEndedAt?.toIsoString(),
        )
    }

    private fun mapActivityContextFallback(sample: ActivitySample): ActivityContextBatchDto {
        val dominant = sample.dominant ?: "unknown"
        val automotiveNow = dominant == "automotive"
        return ActivityContextBatchDto(
            dominant = dominant,
            bestConfidence = sample.confidence ?: "low",
            stationaryShare = if (dominant == "stationary") 1.0 else 0.0,
            walkingShare = if (dominant == "walking") 1.0 else 0.0,
            runningShare = if (dominant == "running") 1.0 else 0.0,
            cyclingShare = if (dominant == "cycling") 1.0 else 0.0,
            automotiveShare = if (dominant == "automotive") 1.0 else 0.0,
            unknownShare = if (dominant == "unknown") 1.0 else 0.0,
            nonAutomotiveStreakSec = if (automotiveNow) 0.0 else 1.0,
            isAutomotiveNow = automotiveNow,
            windowStartedAt = sample.timestamp.toIsoString(),
            windowEndedAt = sample.timestamp.toIsoString(),
        )
    }

    private fun mapPedometer(summary: PedometerSummary): PedometerBatchDto {
        return PedometerBatchDto(
            steps = summary.steps,
            distanceM = NumericSanitizer.sanitizeDouble(summary.distanceM),
            cadence = NumericSanitizer.sanitizeDouble(summary.cadence),
            pace = NumericSanitizer.sanitizeDouble(summary.pace),
        )
    }

    private fun mapAltimeter(summary: AltimeterSummary): AltimeterBatchDto {
        return AltimeterBatchDto(
            relAltMMin = NumericSanitizer.sanitizeDouble(summary.relAltMMin),
            relAltMMax = NumericSanitizer.sanitizeDouble(summary.relAltMMax),
            pressureKpaMin = NumericSanitizer.sanitizeDouble(summary.pressureKpaMin),
            pressureKpaMax = NumericSanitizer.sanitizeDouble(summary.pressureKpaMax),
        )
    }

    private fun mapScreenInteractionContext(summary: ScreenInteractionContextSummary): ScreenInteractionContextBatchDto {
        return ScreenInteractionContextBatchDto(
            count = summary.count,
            recent = summary.recent,
            activeSec = NumericSanitizer.sanitizeDouble(summary.activeSec),
            lastAt = summary.lastAt?.toIsoString(),
            windowStartedAt = summary.windowStartedAt?.toIsoString(),
            windowEndedAt = summary.windowEndedAt?.toIsoString(),
        )
    }

    private fun mapTripConfig(config: EventThresholdSet): TripConfigDto {
        return TripConfigDto(
            v2 = V2ConfigDto(
                speedGateAccelBrakeMs = 3.0,
                speedGateTurnMs = 5.0,
                speedGateCombinedMs = 5.0,
                cooldownAccelBrakeS = 1.2,
                cooldownTurnS = 0.8,
                cooldownCombinedS = 0.8,
                cooldownRoadS = config.roadCooldownS,
                accelSharpG = config.accelSharpG,
                accelEmergencyG = config.accelEmergencyG,
                brakeSharpG = config.brakeSharpG,
                brakeEmergencyG = config.brakeEmergencyG,
                turnSharpLatG = config.turnSharpG,
                turnEmergencyLatG = config.turnEmergencyG,
                combinedLatMinG = 0.35,
                accelInTurnSharpG = config.accelInTurnSharpG ?: 0.22,
                accelInTurnEmergencyG = config.accelInTurnEmergencyG ?: 0.32,
                brakeInTurnSharpG = config.brakeInTurnSharpG ?: 0.22,
                brakeInTurnEmergencyG = config.brakeInTurnEmergencyG ?: 0.32,
                roadWindowS = 0.40,
                roadLowP2PG = 0.70,
                roadHighP2PG = 1.10,
                roadLowAbsG = config.roadLowG ?: 0.45,
                roadHighAbsG = config.roadHighG ?: 0.75,
            ),
            scoring = ScoringConfigDto(
                doubleCountWindowS = 0.6,
                speedFactor = SpeedFactorConfigDto(
                    breakpointsMs = listOf(0.0, 5.0, 13.9, 22.2, 30.6),
                    factors = listOf(0.25, 0.45, 0.75, 1.05, 1.35),
                ),
                penalty = PenaltyConfigDto(
                    accel = ClassPenaltyDto(sharp = 0.3, emergency = 1.0),
                    brake = ClassPenaltyDto(sharp = 0.5, emergency = 1.5),
                    turn = ClassPenaltyDto(sharp = 0.5, emergency = 1.4),
                    accelInTurn = ClassPenaltyDto(sharp = 1.2, emergency = 2.0),
                    brakeInTurn = ClassPenaltyDto(sharp = 1.6, emergency = 2.6),
                    roadAnomaly = SeverityPenaltyDto(low = 0.3, high = 0.8),
                ),
            ),
        )
    }
}

private fun TrackingMode.toWireValue(): String = when (this) {
    TrackingMode.SINGLE_TRIP -> "single_trip"
    TrackingMode.DAY_MONITORING -> "day_monitoring"
}

private fun TelemetryEventType.toWireValue(): String = when (this) {
    TelemetryEventType.ACCEL -> "accel"
    TelemetryEventType.BRAKE -> "brake"
    TelemetryEventType.TURN -> "turn"
    TelemetryEventType.ACCEL_IN_TURN -> "accel_in_turn"
    TelemetryEventType.BRAKE_IN_TURN -> "brake_in_turn"
    TelemetryEventType.ROAD_ANOMALY -> "road_anomaly"
}

private fun Instant.toIsoString(): String = toString()

private fun escapeJson(value: String): String {
    return buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}