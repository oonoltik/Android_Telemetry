package com.alex.android_telemetry.telemetry.model

import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.DeviceMetaDto
import com.alex.android_telemetry.telemetry.trips.api.TripCoreDto
import com.alex.android_telemetry.telemetry.trips.api.TripMetricsRawDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryPayloadDto
import kotlinx.serialization.json.JsonObject

data class FinishPayloadDraft(
    val sessionId: String,
    val driverId: String?,
    val deviceId: String,
    val clientEndedAt: String,

    val trackingMode: String?,
    val transportMode: String?,
    val tripDurationSec: Double?,
    val finishReason: String?,

    val tripCore: TripCoreDto?,
    val deviceMeta: DeviceMetaDto?,
    val clientMetrics: ClientTripMetricsDto?,
    val tripSummary: TripSummaryPayloadDto?,
    val tripMetricsRaw: TripMetricsRawDto?,

    val deviceContext: JsonObject?,
    val tailActivityContext: JsonObject?
)