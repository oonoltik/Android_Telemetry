package com.alex.android_telemetry.telemetry.ingest.mapper

import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.ingest.api.Axis3Dto
import com.alex.android_telemetry.telemetry.ingest.api.DeviceStateBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.HeadingBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.NetworkBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.TelemetryBatchDto
import com.alex.android_telemetry.telemetry.ingest.api.TelemetryEventDto
import com.alex.android_telemetry.telemetry.ingest.api.TelemetrySampleDto
import com.alex.android_telemetry.telemetry.ingest.api.TripConfigDto
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import kotlinx.datetime.Instant

class TelemetryBatchDtoMapper {

    fun map(batch: TelemetryBatch): TelemetryBatchDto = TelemetryBatchDto(
        deviceId = batch.deviceId,
        driverId = batch.driverId,
        sessionId = batch.sessionId,
        timestamp = batch.createdAt.toIsoString(),
        trackingMode = batch.trackingMode?.toWireValue(),
        transportMode = batch.transportMode,
        batchId = batch.batchId,
        batchSeq = batch.batchSeq,
        samples = batch.frames.map(::mapFrame),
        events = batch.events.map(::mapEvent),
        deviceState = batch.deviceState?.let(::mapDeviceState),
        network = batch.networkState?.let(::mapNetwork),
        heading = batch.headingSummary?.let(::mapHeading),
        tripConfig = batch.tripConfig?.let(::mapTripConfig),
    )

    private fun mapFrame(frame: TelemetryFrame): TelemetrySampleDto = TelemetrySampleDto(
        timestamp = frame.timestamp.toIsoString(),
        lat = NumericSanitizer.sanitizeDouble(frame.location?.lat),
        lon = NumericSanitizer.sanitizeDouble(frame.location?.lon),
        horizontalAccuracyM = NumericSanitizer.sanitizeDouble(frame.location?.horizontalAccuracyM),
        verticalAccuracyM = NumericSanitizer.sanitizeDouble(frame.location?.verticalAccuracyM),
        speedMS = NumericSanitizer.sanitizeDouble(frame.location?.speedMS),
        speedAccuracyMS = NumericSanitizer.sanitizeDouble(frame.location?.speedAccuracyMS),
        bearingDeg = NumericSanitizer.sanitizeDouble(frame.location?.bearingDeg),
        bearingAccuracyDeg = NumericSanitizer.sanitizeDouble(frame.location?.bearingAccuracyDeg),
        provider = frame.location?.provider,
        accel = if (frame.imu?.accelX != null || frame.imu?.accelY != null || frame.imu?.accelZ != null) {
            Axis3Dto(
                x = NumericSanitizer.sanitizeDouble(frame.imu?.accelX),
                y = NumericSanitizer.sanitizeDouble(frame.imu?.accelY),
                z = NumericSanitizer.sanitizeDouble(frame.imu?.accelZ),
            )
        } else {
            null
        },
        rotation = if (frame.imu?.gyroX != null || frame.imu?.gyroY != null || frame.imu?.gyroZ != null) {
            Axis3Dto(
                x = NumericSanitizer.sanitizeDouble(frame.imu?.gyroX),
                y = NumericSanitizer.sanitizeDouble(frame.imu?.gyroY),
                z = NumericSanitizer.sanitizeDouble(frame.imu?.gyroZ),
            )
        } else {
            null
        },
        headingDeg = NumericSanitizer.sanitizeDouble(
            frame.heading?.trueHeadingDeg ?: frame.heading?.magneticHeadingDeg,
        ),
        headingAccuracyDeg = NumericSanitizer.sanitizeDouble(frame.heading?.accuracyDeg),
        longitudinalAccelG = NumericSanitizer.sanitizeDouble(frame.motionVector?.aLongG),
        lateralAccelG = NumericSanitizer.sanitizeDouble(frame.motionVector?.aLatG),
        verticalAccelG = NumericSanitizer.sanitizeDouble(frame.motionVector?.aVertG),
        yawRate = NumericSanitizer.sanitizeDouble(frame.motionVector?.yawRate),
    )

    private fun mapEvent(event: DetectedTelemetryEvent): TelemetryEventDto = TelemetryEventDto(
        type = event.type.toWireValue(),
        timestamp = event.timestamp.toIsoString(),
        intensity = NumericSanitizer.sanitizeDouble(event.intensity) ?: 0.0,
        speedMS = NumericSanitizer.sanitizeDouble(event.speedMS),
        eventClass = event.eventClass,
        subtype = event.subtype,
        severity = event.severity,
        details = event.details,
        origin = event.origin,
        algoVersion = event.algoVersion,
        meta = event.meta,
    )

    private fun mapDeviceState(snapshot: DeviceStateSnapshot): DeviceStateBatchDto = DeviceStateBatchDto(
        timestamp = snapshot.timestamp.toIsoString(),
        batteryLevel = NumericSanitizer.sanitizeDouble(snapshot.batteryLevel),
        batteryState = snapshot.batteryState,
        lowPowerMode = snapshot.lowPowerMode,
        isCharging = snapshot.isCharging,
    )

    private fun mapNetwork(snapshot: NetworkStateSnapshot): NetworkBatchDto = NetworkBatchDto(
        timestamp = snapshot.timestamp.toIsoString(),
        status = snapshot.status,
        interfaceType = snapshot.interfaceType,
        isExpensive = snapshot.isExpensive,
        isConstrained = snapshot.isConstrained,
    )

    private fun mapHeading(sample: HeadingSample): HeadingBatchDto = HeadingBatchDto(
        timestamp = sample.timestamp.toIsoString(),
        trueHeadingDeg = NumericSanitizer.sanitizeDouble(sample.trueHeadingDeg),
        magneticHeadingDeg = NumericSanitizer.sanitizeDouble(sample.magneticHeadingDeg),
        accuracyDeg = NumericSanitizer.sanitizeDouble(sample.accuracyDeg),
    )

    private fun mapTripConfig(config: EventThresholdSet): TripConfigDto = TripConfigDto(
        accelSharpG = config.accelSharpG,
        accelEmergencyG = config.accelEmergencyG,
        brakeSharpG = config.brakeSharpG,
        brakeEmergencyG = config.brakeEmergencyG,
        turnSharpG = config.turnSharpG,
        turnEmergencyG = config.turnEmergencyG,
        roadLowG = config.roadLowG,
        roadHighG = config.roadHighG,
    )
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