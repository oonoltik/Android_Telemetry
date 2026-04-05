package com.alex.android_telemetry.telemetry.ingest.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryBatchDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("app_build") val appBuild: String? = null,
    @SerialName("ios_version") val iosVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("tracking_mode") val trackingMode: String? = null,
    @SerialName("transport_mode") val transportMode: String? = null,
    @SerialName("batch_id") val batchId: String,
    @SerialName("batch_seq") val batchSeq: Int,
    @SerialName("samples") val samples: List<TelemetrySampleDto>,
    @SerialName("events") val events: List<TelemetryEventDto> = emptyList(),
    @SerialName("trip_config") val tripConfig: TripConfigDto? = null,
    @SerialName("motion_activity") val motionActivity: MotionActivityBatchDto? = null,
    @SerialName("pedometer") val pedometer: PedometerBatchDto? = null,
    @SerialName("altimeter") val altimeter: AltimeterBatchDto? = null,
    @SerialName("device_state") val deviceState: DeviceStateBatchDto? = null,
    @SerialName("network") val network: NetworkBatchDto? = null,
    @SerialName("heading") val heading: HeadingBatchDto? = null,
    @SerialName("activity_context") val activityContext: ActivityContextBatchDto? = null,
    @SerialName("screen_interaction_context") val screenInteractionContext: ScreenInteractionContextBatchDto? = null,
)

@Serializable
data class TelemetrySampleDto(
    @SerialName("t") val t: String,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    @SerialName("h_acc") val hAcc: Double? = null,
    @SerialName("v_acc") val vAcc: Double? = null,
    @SerialName("speed_m_s") val speedMS: Double? = null,
    @SerialName("speed_acc") val speedAcc: Double? = null,
    @SerialName("course") val course: Double? = null,
    @SerialName("course_acc") val courseAcc: Double? = null,
    @SerialName("accel") val accel: Axis3Dto? = null,
    @SerialName("rotation") val rotation: Axis3Dto? = null,
    @SerialName("attitude") val attitude: AttitudeDto? = null,
    @SerialName("a_long_g") val aLongG: Double? = null,
    @SerialName("a_lat_g") val aLatG: Double? = null,
    @SerialName("a_vert_g") val aVertG: Double? = null,
)

@Serializable
data class Axis3Dto(
    @SerialName("x") val x: Double? = null,
    @SerialName("y") val y: Double? = null,
    @SerialName("z") val z: Double? = null,
)

@Serializable
data class AttitudeDto(
    @SerialName("yaw") val yaw: Double? = null,
    @SerialName("pitch") val pitch: Double? = null,
    @SerialName("roll") val roll: Double? = null,
)

@Serializable
data class TelemetryEventDto(
    @SerialName("type") val type: String,
    @SerialName("t") val t: String,
    @SerialName("intensity") val intensity: Double,
    @SerialName("details") val details: String? = null,
    @SerialName("origin") val origin: String? = null,
    @SerialName("algo_version") val algoVersion: String? = null,
    @SerialName("speed_m_s") val speedMS: Double? = null,
    @SerialName("class") val eventClass: String? = null,
    @SerialName("subtype") val subtype: String? = null,
    @SerialName("severity") val severity: String? = null,
    @SerialName("meta_json") val metaJson: String? = null,
)

@Serializable
data class MotionActivityBatchDto(
    @SerialName("dominant") val dominant: String? = null,
    @SerialName("confidence") val confidence: String? = null,
    @SerialName("durations_sec") val durationsSec: Map<String, Double> = emptyMap(),
)

@Serializable
data class ActivityContextBatchDto(
    @SerialName("dominant") val dominant: String? = null,
    @SerialName("best_confidence") val bestConfidence: String? = null,
    @SerialName("stationary_share") val stationaryShare: Double? = null,
    @SerialName("walking_share") val walkingShare: Double? = null,
    @SerialName("running_share") val runningShare: Double? = null,
    @SerialName("cycling_share") val cyclingShare: Double? = null,
    @SerialName("automotive_share") val automotiveShare: Double? = null,
    @SerialName("unknown_share") val unknownShare: Double? = null,
    @SerialName("non_automotive_streak_sec") val nonAutomotiveStreakSec: Double? = null,
    @SerialName("is_automotive_now") val isAutomotiveNow: Boolean? = null,
    @SerialName("window_started_at") val windowStartedAt: String? = null,
    @SerialName("window_ended_at") val windowEndedAt: String? = null,
)

@Serializable
data class ScreenInteractionContextBatchDto(
    @SerialName("count") val count: Int? = null,
    @SerialName("recent") val recent: Boolean? = null,
    @SerialName("active_sec") val activeSec: Double? = null,
    @SerialName("last_at") val lastAt: String? = null,
    @SerialName("window_started_at") val windowStartedAt: String? = null,
    @SerialName("window_ended_at") val windowEndedAt: String? = null,
)

@Serializable
data class PedometerBatchDto(
    @SerialName("steps") val steps: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("cadence") val cadence: Double? = null,
    @SerialName("pace") val pace: Double? = null,
)

@Serializable
data class AltimeterBatchDto(
    @SerialName("rel_alt_m_min") val relAltMMin: Double? = null,
    @SerialName("rel_alt_m_max") val relAltMMax: Double? = null,
    @SerialName("pressure_kpa_min") val pressureKpaMin: Double? = null,
    @SerialName("pressure_kpa_max") val pressureKpaMax: Double? = null,
)

@Serializable
data class DeviceStateBatchDto(
    @SerialName("battery_level") val batteryLevel: Double? = null,
    @SerialName("battery_state") val batteryState: String? = null,
    @SerialName("low_power_mode") val lowPowerMode: Boolean? = null,
)

@Serializable
data class NetworkBatchDto(
    @SerialName("status") val status: String? = null,
    @SerialName("interface") val interfaceName: String? = null,
    @SerialName("expensive") val expensive: Boolean? = null,
    @SerialName("constrained") val constrained: Boolean? = null,
)

@Serializable
data class HeadingBatchDto(
    @SerialName("magnetic_deg") val magneticDeg: Double? = null,
    @SerialName("true_deg") val trueDeg: Double? = null,
    @SerialName("accuracy_deg") val accuracyDeg: Double? = null,
)

@Serializable
data class ClassPenaltyDto(
    @SerialName("sharp") val sharp: Double,
    @SerialName("emergency") val emergency: Double,
)

@Serializable
data class SeverityPenaltyDto(
    @SerialName("low") val low: Double,
    @SerialName("high") val high: Double,
)

@Serializable
data class SpeedFactorConfigDto(
    @SerialName("breakpoints_ms") val breakpointsMs: List<Double>,
    @SerialName("factors") val factors: List<Double>,
)

@Serializable
data class PenaltyConfigDto(
    @SerialName("accel") val accel: ClassPenaltyDto,
    @SerialName("brake") val brake: ClassPenaltyDto,
    @SerialName("turn") val turn: ClassPenaltyDto,
    @SerialName("accel_in_turn") val accelInTurn: ClassPenaltyDto,
    @SerialName("brake_in_turn") val brakeInTurn: ClassPenaltyDto,
    @SerialName("road_anomaly") val roadAnomaly: SeverityPenaltyDto,
)

@Serializable
data class ScoringConfigDto(
    @SerialName("double_count_window_s") val doubleCountWindowS: Double,
    @SerialName("speed_factor") val speedFactor: SpeedFactorConfigDto,
    @SerialName("penalty") val penalty: PenaltyConfigDto,
)

@Serializable
data class V2ConfigDto(
    @SerialName("speed_gate_accel_brake_ms") val speedGateAccelBrakeMs: Double,
    @SerialName("speed_gate_turn_ms") val speedGateTurnMs: Double,
    @SerialName("speed_gate_combined_ms") val speedGateCombinedMs: Double,
    @SerialName("cooldown_accel_brake_s") val cooldownAccelBrakeS: Double,
    @SerialName("cooldown_turn_s") val cooldownTurnS: Double,
    @SerialName("cooldown_combined_s") val cooldownCombinedS: Double,
    @SerialName("cooldown_road_s") val cooldownRoadS: Double,
    @SerialName("accel_sharp_g") val accelSharpG: Double,
    @SerialName("accel_emergency_g") val accelEmergencyG: Double,
    @SerialName("brake_sharp_g") val brakeSharpG: Double,
    @SerialName("brake_emergency_g") val brakeEmergencyG: Double,
    @SerialName("turn_sharp_lat_g") val turnSharpLatG: Double,
    @SerialName("turn_emergency_lat_g") val turnEmergencyLatG: Double,
    @SerialName("combined_lat_min_g") val combinedLatMinG: Double,
    @SerialName("accel_in_turn_sharp_g") val accelInTurnSharpG: Double,
    @SerialName("accel_in_turn_emergency_g") val accelInTurnEmergencyG: Double,
    @SerialName("brake_in_turn_sharp_g") val brakeInTurnSharpG: Double,
    @SerialName("brake_in_turn_emergency_g") val brakeInTurnEmergencyG: Double,
    @SerialName("road_window_s") val roadWindowS: Double,
    @SerialName("road_low_p2p_g") val roadLowP2PG: Double,
    @SerialName("road_high_p2p_g") val roadHighP2PG: Double,
    @SerialName("road_low_abs_g") val roadLowAbsG: Double,
    @SerialName("road_high_abs_g") val roadHighAbsG: Double,
)

@Serializable
data class TripConfigDto(
    @SerialName("v2") val v2: V2ConfigDto,
    @SerialName("scoring") val scoring: ScoringConfigDto,
)