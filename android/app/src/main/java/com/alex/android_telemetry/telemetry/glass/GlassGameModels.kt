package com.alex.android_telemetry.telemetry.glass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GlassGameBatchDto(
    @SerialName("device_id")
    val deviceId: String,

    @SerialName("driver_id")
    val driverId: String?,

    @SerialName("session_id")
    val sessionId: String,

    @SerialName("game_id")
    val gameId: String,

    @SerialName("window_opened_at")
    val windowOpenedAt: String,

    @SerialName("game_started_at")
    val gameStartedAt: String?,

    @SerialName("game_ended_at")
    val gameEndedAt: String?,

    @SerialName("window_closed_at")
    val windowClosedAt: String,

    @SerialName("max_spill_level")
    val maxSpillLevel: Double?,

    @SerialName("total_refilled_01")
    val totalRefilled01: Double?,

    @SerialName("game_duration_sec")
    val gameDurationSec: Double?,

    @SerialName("window_duration_sec")
    val windowDurationSec: Double?,

    @SerialName("background_events")
    val backgroundEvents: Map<String, Double>,

    val analytics: Map<String, Double>?,

    val aborted: Boolean,
)