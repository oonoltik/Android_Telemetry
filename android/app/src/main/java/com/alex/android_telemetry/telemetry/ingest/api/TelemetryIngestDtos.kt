package com.alex.android_telemetry.telemetry.ingest.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryBatchDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("tracking_mode") val trackingMode: String? = null,
    @SerialName("transport_mode") val transportMode: String? = null,
    @SerialName("batch_id") val batchId: String,
    @SerialName("batch_seq") val batchSeq: Int,
    @SerialName("samples") val samples: List<TelemetrySampleDto>,
    @SerialName("events") val events: List<TelemetryEventDto> = emptyList(),
    @SerialName("device_state") val deviceState: DeviceStateBatchDto? = null,
    @SerialName("network") val network: NetworkBatchDto? = null,
    @SerialName("heading") val heading: HeadingBatchDto? = null,
    @SerialName("trip_config") val tripConfig: TripConfigDto? = null,
)

@Serializable
data class TelemetrySampleDto(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    @SerialName("h_acc") val horizontalAccuracyM: Double? = null,
    @SerialName("v_acc") val verticalAccuracyM: Double? = null,
    @SerialName("speed_m_s") val speedMS: Double? = null,
    @SerialName("speed_acc_m_s") val speedAccuracyMS: Double? = null,
    @SerialName("bearing_deg") val bearingDeg: Double? = null,
    @SerialName("bearing_acc_deg") val bearingAccuracyDeg: Double? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("accel") val accel: Axis3Dto? = null,
    @SerialName("rotation") val rotation: Axis3Dto? = null,
    @SerialName("heading_deg") val headingDeg: Double? = null,
    @SerialName("heading_accuracy_deg") val headingAccuracyDeg: Double? = null,
    @SerialName("a_long_g") val longitudinalAccelG: Double? = null,
    @SerialName("a_lat_g") val lateralAccelG: Double? = null,
    @SerialName("a_vert_g") val verticalAccelG: Double? = null,
    @SerialName("yaw_rate") val yawRate: Double? = null,
)

@Serializable
data class Axis3Dto(
    @SerialName("x") val x: Double? = null,
    @SerialName("y") val y: Double? = null,
    @SerialName("z") val z: Double? = null,
)

@Serializable
data class TelemetryEventDto(
    @SerialName("type") val type: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("intensity") val intensity: Double,
    @SerialName("speed_m_s") val speedMS: Double? = null,
    @SerialName("class") val eventClass: String? = null,
    @SerialName("subtype") val subtype: String? = null,
    @SerialName("severity") val severity: String? = null,
    @SerialName("details") val details: String? = null,
    @SerialName("origin") val origin: String? = null,
    @SerialName("algo_version") val algoVersion: String? = null,
    @SerialName("meta") val meta: Map<String, String> = emptyMap(),
)

@Serializable
data class DeviceStateBatchDto(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("battery_level") val batteryLevel: Double? = null,
    @SerialName("battery_state") val batteryState: String? = null,
    @SerialName("low_power_mode") val lowPowerMode: Boolean? = null,
    @SerialName("is_charging") val isCharging: Boolean? = null,
)

@Serializable
data class NetworkBatchDto(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("status") val status: String? = null,
    @SerialName("interface_type") val interfaceType: String? = null,
    @SerialName("is_expensive") val isExpensive: Boolean? = null,
    @SerialName("is_constrained") val isConstrained: Boolean? = null,
)

@Serializable
data class HeadingBatchDto(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("true_heading_deg") val trueHeadingDeg: Double? = null,
    @SerialName("magnetic_heading_deg") val magneticHeadingDeg: Double? = null,
    @SerialName("accuracy_deg") val accuracyDeg: Double? = null,
)

@Serializable
data class TripConfigDto(
    @SerialName("accel_sharp_g") val accelSharpG: Double,
    @SerialName("accel_emergency_g") val accelEmergencyG: Double,
    @SerialName("brake_sharp_g") val brakeSharpG: Double,
    @SerialName("brake_emergency_g") val brakeEmergencyG: Double,
    @SerialName("turn_sharp_g") val turnSharpG: Double,
    @SerialName("turn_emergency_g") val turnEmergencyG: Double,
    @SerialName("road_low_g") val roadLowG: Double? = null,
    @SerialName("road_high_g") val roadHighG: Double? = null,
)