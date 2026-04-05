package com.alex.android_telemetry.telemetry.trips.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClientAggDto(
    @SerialName("count") val count: Int,
    @SerialName("sum_intensity") val sumIntensity: Double,
    @SerialName("max_intensity") val maxIntensity: Double,
    @SerialName("count_per_km") val countPerKm: Double,
    @SerialName("sum_per_km") val sumPerKm: Double,
)

@Serializable
data class ClientTripMetricsDto(
    @SerialName("trip_distance_m") val tripDistanceM: Double,
    @SerialName("trip_distance_km_from_gps") val tripDistanceKmFromGps: Double,
    @SerialName("brake") val brake: ClientAggDto,
    @SerialName("accel") val accel: ClientAggDto,
    @SerialName("road") val road: ClientAggDto,
    @SerialName("turn") val turn: ClientAggDto,
)

@Serializable
data class TripCoreDto(
    @SerialName("trip_id") val tripId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("client_ended_at") val clientEndedAt: String,
)

@Serializable
data class DeviceMetaDto(
    @SerialName("platform") val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("app_build") val appBuild: String? = null,
    @SerialName("ios_version") val iosVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("timezone") val timezone: String? = null,
)

@Serializable
data class TripSummaryPayloadDto(
    @SerialName("score_v2") val scoreV2: Double,
    @SerialName("driving_load") val drivingLoad: Double,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double,
    @SerialName("driving_mode") val drivingMode: String,
    @SerialName("trip_duration_sec") val tripDurationSec: Double,
)

@Serializable
data class TripMetricsRawDto(
    @SerialName("trip_distance_m") val tripDistanceM: Double,
    @SerialName("trip_distance_km_from_gps") val tripDistanceKmFromGps: Double,
    @SerialName("brake") val brake: ClientAggDto,
    @SerialName("accel") val accel: ClientAggDto,
    @SerialName("turn") val turn: ClientAggDto,
    @SerialName("road") val road: ClientAggDto,
)

@Serializable
data class PendingTripFinishDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("client_ended_at") val clientEndedAt: String,
    @SerialName("created_at") val createdAt: String,

    @SerialName("trip_core") val tripCore: TripCoreDto,
    @SerialName("device_meta") val deviceMeta: DeviceMetaDto,

    @SerialName("tracking_mode") val trackingMode: String? = null,
    @SerialName("transport_mode") val transportMode: String? = null,
    @SerialName("trip_duration_sec") val tripDurationSec: Double? = null,
    @SerialName("finish_reason") val finishReason: String? = null,

    @SerialName("client_metrics") val clientMetrics: ClientTripMetricsDto? = null,
    @SerialName("trip_summary") val tripSummary: TripSummaryPayloadDto? = null,
    @SerialName("trip_metrics_raw") val tripMetricsRaw: TripMetricsRawDto? = null,

    @SerialName("device_context") val deviceContext: JsonObject? = null,
    @SerialName("tail_activity_context") val tailActivityContext: JsonObject? = null,

    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("app_build") val appBuild: String? = null,
    @SerialName("ios_version") val iosVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("timezone") val timezone: String? = null,

    @SerialName("retry_count") val retryCount: Int = 0,
    @SerialName("last_attempt_at") val lastAttemptAt: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("queued_because_no_delivered_batches") val queuedBecauseNoDeliveredBatches: Boolean = false,
)

@Serializable
data class FinishCommand(
    @SerialName("session_id") val sessionId: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("client_ended_at") val clientEndedAt: String,
    @SerialName("tracking_mode") val trackingMode: String? = null,
    @SerialName("transport_mode") val transportMode: String? = null,
    @SerialName("trip_duration_sec") val tripDurationSec: Double? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    @SerialName("client_metrics") val clientMetrics: ClientTripMetricsDto? = null,
    @SerialName("trip_summary") val tripSummary: TripSummaryPayloadDto? = null,
    @SerialName("trip_metrics_raw") val tripMetricsRaw: TripMetricsRawDto? = null,
    @SerialName("device_context") val deviceContext: JsonObject? = null,
    @SerialName("tail_activity_context") val tailActivityContext: JsonObject? = null,
)

@Serializable
data class TripSummaryDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("client_started_at") val clientStartedAt: String? = null,
    @SerialName("client_ended_at") val clientEndedAt: String? = null,
    @SerialName("received_started_at") val receivedStartedAt: String? = null,
    @SerialName("received_ended_at") val receivedEndedAt: String? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("trip_score") val tripScore: Double? = null,
    @SerialName("trip_score_exposure") val tripScoreExposure: Double? = null,
    @SerialName("trip_preset") val tripPreset: String? = null,
    @SerialName("score_v2") val scoreV2: Double? = null,
    @SerialName("driving_load") val drivingLoad: Double? = null,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double? = null,
    @SerialName("driving_mode") val drivingMode: String? = null,
    @SerialName("trip_duration_sec") val tripDurationSec: Double? = null,
)

@Serializable
data class RecentTripsResponseDto(
    @SerialName("status") val status: String,
    @SerialName("trips") val trips: List<TripSummaryDto>,
)

@Serializable
data class DriverHomeResponseDto(
    @SerialName("status") val status: String,
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("rating_status") val ratingStatus: String,
    @SerialName("trip_count") val tripCount: Int,
    @SerialName("avg_score") val avgScore: Double? = null,
    @SerialName("score_delta_recent") val scoreDeltaRecent: Double? = null,
    @SerialName("better_than_drivers_pct") val betterThanDriversPct: Double? = null,
    @SerialName("driver_rank") val driverRank: Int? = null,
    @SerialName("total_drivers") val totalDrivers: Int,
    @SerialName("driver_level") val driverLevel: String? = null,
    @SerialName("next_level") val nextLevel: String? = null,
    @SerialName("points_to_next_level") val pointsToNextLevel: Double? = null,
    @SerialName("recent_trip_scores") val recentTripScores: List<Double> = emptyList(),
    @SerialName("recent_trip_colors") val recentTripColors: List<String> = emptyList(),
    @SerialName("trips_to_unlock_percentile") val tripsToUnlockPercentile: Int,
)

@Serializable
data class TripReportDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("device_id") val deviceId: String,

    @SerialName("client_started_at") val clientStartedAt: String? = null,
    @SerialName("client_ended_at") val clientEndedAt: String? = null,
    @SerialName("received_started_at") val receivedStartedAt: String? = null,
    @SerialName("received_ended_at") val receivedEndedAt: String? = null,

    @SerialName("batches_count") val batchesCount: Int = 0,
    @SerialName("samples_count") val samplesCount: Int = 0,
    @SerialName("events_count") val eventsCount: Int = 0,

    @SerialName("distance_km") val distanceKm: Double? = 0.0,
    @SerialName("stops_count") val stopsCount: Int? = 0,
    @SerialName("stops_total_sec") val stopsTotalSec: Double? = 0.0,
    @SerialName("stops_p95_sec") val stopsP95Sec: Double? = 0.0,
    @SerialName("stops_per_km") val stopsPerKm: Double? = null,

    @SerialName("accel_sharp_total") val accelSharpTotal: Int = 0,
    @SerialName("accel_emergency_total") val accelEmergencyTotal: Int = 0,
    @SerialName("brake_sharp_total") val brakeSharpTotal: Int = 0,
    @SerialName("brake_emergency_total") val brakeEmergencyTotal: Int = 0,
    @SerialName("turn_sharp_total") val turnSharpTotal: Int = 0,
    @SerialName("turn_emergency_total") val turnEmergencyTotal: Int = 0,
    @SerialName("accel_in_turn_sharp_total") val accelInTurnSharpTotal: Int = 0,
    @SerialName("accel_in_turn_emergency_total") val accelInTurnEmergencyTotal: Int = 0,
    @SerialName("brake_in_turn_sharp_total") val brakeInTurnSharpTotal: Int = 0,
    @SerialName("brake_in_turn_emergency_total") val brakeInTurnEmergencyTotal: Int = 0,
    @SerialName("road_anomaly_low_total") val roadAnomalyLowTotal: Int = 0,
    @SerialName("road_anomaly_high_total") val roadAnomalyHighTotal: Int = 0,

    @SerialName("trip_score") val tripScore: Double = 0.0,
    @SerialName("trip_score_exposure") val tripScoreExposure: Double? = null,
    @SerialName("trip_preset") val tripPreset: String? = null,
    @SerialName("trip_penalty_total") val tripPenaltyTotal: Double? = null,
    @SerialName("worst_batch_score") val worstBatchScore: Double = 0.0,

    @SerialName("speed_max_kmh") val speedMaxKmh: Double? = null,
    @SerialName("speed_avg_kmh") val speedAvgKmh: Double? = null,
    @SerialName("speed_p95_kmh") val speedP95Kmh: Double? = null,

    @SerialName("accel_x_min") val accelXMin: Double? = null,
    @SerialName("accel_x_max") val accelXMax: Double? = null,
    @SerialName("accel_y_abs_max") val accelYAbsMax: Double? = null,
    @SerialName("accel_z_abs_max") val accelZAbsMax: Double? = null,
    @SerialName("gyro_z_abs_max") val gyroZAbsMax: Double? = null,

    @SerialName("batch_seq_max") val batchSeqMax: Int? = null,
    @SerialName("batches_missing_count") val batchesMissingCount: Int? = null,
    @SerialName("batches_missing_seqs") val batchesMissingSeqs: String? = null,

    @SerialName("gps_points") val gpsPoints: Int? = null,
    @SerialName("gps_1hz_points") val gps1HzPoints: Int? = null,
    @SerialName("gps_hacc_p95_m") val gpsHaccP95M: Double? = null,
    @SerialName("gps_good_100_share") val gpsGood100Share: Double? = null,
    @SerialName("gps_unique_coords_5dp") val gpsUniqueCoords5dp: Int? = null,
    @SerialName("gps_span_m") val gpsSpanM: Double? = null,
    @SerialName("gps_is_stuck") val gpsIsStuck: Int? = null,
    @SerialName("gps_quality_score") val gpsQualityScore: Int? = null,

    @SerialName("better_than_prev_pct") val betterThanPrevPct: Double? = null,
    @SerialName("better_than_all_pct") val betterThanAllPct: Double? = null,
    @SerialName("prev_trips_count") val prevTripsCount: Int? = null,
    @SerialName("all_trips_count") val allTripsCount: Int? = null,
    @SerialName("driver_rank") val driverRank: Int? = null,
    @SerialName("total_drivers") val totalDrivers: Int? = null,
    @SerialName("driver_avg_score") val driverAvgScore: Double? = null,
    @SerialName("driver_trips_total") val driverTripsTotal: Int? = null,

    @SerialName("score_v2") val scoreV2: Double? = null,
    @SerialName("driving_load") val drivingLoad: Double? = null,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double? = null,
    @SerialName("driving_mode") val drivingMode: String? = null,
    @SerialName("trip_duration_sec") val tripDurationSec: Double? = null,
) {
    val accelInTurnTotal: Int
        get() = accelInTurnSharpTotal + accelInTurnEmergencyTotal

    val brakeInTurnTotal: Int
        get() = brakeInTurnSharpTotal + brakeInTurnEmergencyTotal
}