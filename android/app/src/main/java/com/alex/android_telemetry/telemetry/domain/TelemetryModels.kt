package com.alex.android_telemetry.telemetry.domain.model

import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import kotlinx.datetime.Instant

// Raw / normalized inputs

data class ImuSample(
    val timestamp: Instant,
    val accelX: Double? = null,
    val accelY: Double? = null,
    val accelZ: Double? = null,
    val gyroX: Double? = null,
    val gyroY: Double? = null,
    val gyroZ: Double? = null,

    val yaw: Double? = null,
    val pitch: Double? = null,
    val roll: Double? = null,
)

data class LocationFix(
    val timestamp: Instant,
    val lat: Double,
    val lon: Double,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val speedMS: Double? = null,
    val speedAccuracyMS: Double? = null,
    val bearingDeg: Double? = null,
    val bearingAccuracyDeg: Double? = null,
    val provider: String? = null,
)

data class HeadingSample(
    val timestamp: Instant,
    val trueHeadingDeg: Double? = null,
    val magneticHeadingDeg: Double? = null,
    val accuracyDeg: Double? = null,
)

data class DeviceStateSnapshot(
    val timestamp: Instant,
    val batteryLevel: Double? = null,
    val batteryState: String? = null,
    val lowPowerMode: Boolean? = null,
    val isCharging: Boolean? = null,
)

data class NetworkStateSnapshot(
    val timestamp: Instant,
    val status: String? = null,
    val interfaceType: String? = null,
    val isExpensive: Boolean? = null,
    val isConstrained: Boolean? = null,
)

data class ActivitySample(
    val timestamp: Instant,
    val dominant: String? = null,
    val confidence: String? = null,
)

data class MotionActivitySummary(
    val dominant: String? = null,
    val confidence: String? = null,
    val durationsSec: Map<String, Double> = emptyMap(),
)

data class ActivityContextSummary(
    val dominant: String? = null,
    val bestConfidence: String? = null,
    val stationaryShare: Double? = null,
    val walkingShare: Double? = null,
    val runningShare: Double? = null,
    val cyclingShare: Double? = null,
    val automotiveShare: Double? = null,
    val unknownShare: Double? = null,
    val nonAutomotiveStreakSec: Double? = null,
    val isAutomotiveNow: Boolean? = null,
    val windowStartedAt: Instant? = null,
    val windowEndedAt: Instant? = null,
)

data class PedometerSummary(
    val steps: Int? = null,
    val distanceM: Double? = null,
    val cadence: Double? = null,
    val pace: Double? = null,
)

data class AltimeterSummary(
    val relAltMMin: Double? = null,
    val relAltMMax: Double? = null,
    val pressureKpaMin: Double? = null,
    val pressureKpaMax: Double? = null,
)

data class ScreenInteractionContextSummary(
    val count: Int? = null,
    val recent: Boolean? = null,
    val activeSec: Double? = null,
    val lastAt: Instant? = null,
    val windowStartedAt: Instant? = null,
    val windowEndedAt: Instant? = null,
)

enum class TrackingMode {
    SINGLE_TRIP,
    DAY_MONITORING,
}

enum class TelemetryMode {
    IDLE,
    ARMED,
    COLLECTING,
    PAUSED,
    FINISHING,
}

enum class TripFinishUiState {
    IDLE,
    FINISHING_IN_PROGRESS,
    FINISH_QUEUED,
    FINISHED_WITH_REPORT,
    FINISH_FAILED,
}

data class TripRuntimeState(
    val sessionId: String? = null,
    val trackingMode: TrackingMode? = null,
    val telemetryMode: TelemetryMode = TelemetryMode.IDLE,
    val startedAt: Instant? = null,
    val lastSampleAt: Instant? = null,
    val lastLocationAt: Instant? = null,
    val lastEventAt: Instant? = null,
    val distanceM: Double = 0.0,
    val isForegroundCollection: Boolean = false,
    val pendingFinish: Boolean = false,
    val finishUiState: TripFinishUiState = TripFinishUiState.IDLE,
    val lastTripReport: TripReportDto? = null,
    val lastFinishError: String? = null,
    val dayMonitoringEnabled: Boolean = false,
    val dayMonitoringAutoTripActive: Boolean = false,
    val dayMonitoringAutoStartedSessionId: String? = null,
    val counters: TelemetryCounters = TelemetryCounters(),
    val routeStats: DeliveryRouteStats = DeliveryRouteStats(),
)

enum class TelemetryEventType {
    ACCEL,
    BRAKE,
    TURN,
    ACCEL_IN_TURN,
    BRAKE_IN_TURN,
    ROAD_ANOMALY,
}

data class MotionVector(
    val aLongG: Double? = null,
    val aLatG: Double? = null,
    val aVertG: Double? = null,
    val yawRate: Double? = null,
    val speedMS: Double? = null,
)

data class DetectedTelemetryEvent(
    val type: TelemetryEventType,
    val timestamp: Instant,
    val intensity: Double,
    val speedMS: Double? = null,
    val eventClass: String? = null,
    val subtype: String? = null,
    val severity: String? = null,
    val details: String? = null,
    val origin: String = "client",
    val algoVersion: String? = null,
    val meta: Map<String, String> = emptyMap(),
)

data class EventThresholdSet(
    val accelSharpG: Double,
    val accelEmergencyG: Double,
    val brakeSharpG: Double,
    val brakeEmergencyG: Double,
    val turnSharpG: Double,
    val turnEmergencyG: Double,
    val roadLowG: Double? = null,
    val roadHighG: Double? = null,
    val minSpeedForAccelBrakeMS: Double = 3.0,
    val minSpeedForTurnMS: Double = 5.0,
    val accelBrakeCooldownS: Double = 1.2,
    val turnCooldownS: Double = 0.8,
    val roadCooldownS: Double = 1.2,
    val roadWindowS: Double = 0.4,
    val combinedLatMinG: Double = 0.35,
    val accelInTurnSharpG: Double? = null,
    val accelInTurnEmergencyG: Double? = null,
    val brakeInTurnSharpG: Double? = null,
    val brakeInTurnEmergencyG: Double? = null,
    val combinedCooldownS: Double = 0.8,
)

data class Attitude(
    val yaw: Double? = null,
    val pitch: Double? = null,
    val roll: Double? = null,
)

data class TelemetryFrame(
    val timestamp: Instant,
    val location: LocationFix? = null,
    val imu: ImuSample? = null,
    val heading: HeadingSample? = null,
    val deviceState: DeviceStateSnapshot? = null,
    val networkState: NetworkStateSnapshot? = null,
    val motionVector: MotionVector? = null,
    val attitude: Attitude? = null,
)

data class TelemetryBatch(
    val deviceId: String,
    val driverId: String? = null,
    val sessionId: String,
    val createdAt: Instant,
    val trackingMode: TrackingMode? = null,
    val transportMode: String? = null,
    val batchId: String,
    val batchSeq: Int,
    val frames: List<TelemetryFrame>,
    val events: List<DetectedTelemetryEvent>,
    val deviceState: DeviceStateSnapshot? = null,
    val networkState: NetworkStateSnapshot? = null,
    val headingSummary: HeadingSample? = null,

    val activitySummary: ActivitySample? = null,

    val motionActivitySummary: MotionActivitySummary? = null,
    val activityContextSummary: ActivityContextSummary? = null,
    val pedometerSummary: PedometerSummary? = null,
    val altimeterSummary: AltimeterSummary? = null,
    val screenInteractionContextSummary: ScreenInteractionContextSummary? = null,

    val tripConfig: EventThresholdSet? = null,
)

data class PedometerSample(
    val timestamp: Instant,
    val steps: Int? = null,
    val distanceM: Double? = null,
    val cadence: Double? = null,
    val pace: Double? = null,
)

data class AltimeterSample(
    val timestamp: Instant,
    val relativeAltitudeM: Double? = null,
    val pressureKpa: Double? = null,
)

data class ScreenInteractionSample(
    val timestamp: Instant,
    val activeStartedAt: Instant? = null,
    val activeEndedAt: Instant? = null,
)