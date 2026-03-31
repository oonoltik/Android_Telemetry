package com.alex.android_telemetry.telemetry.capture.location

import com.alex.android_telemetry.telemetry.model.TelemetrySampleDraft
import kotlinx.coroutines.flow.Flow

interface LocationTelemetrySource {
    fun observeSamples(): Flow<TelemetrySampleDraft>
    suspend fun start()
    suspend fun stop()
}
