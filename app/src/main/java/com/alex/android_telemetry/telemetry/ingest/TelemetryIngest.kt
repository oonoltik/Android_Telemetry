package com.alex.android_telemetry.telemetry.ingest

import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch

fun interface TelemetryBatchEnqueuer {
    suspend fun enqueue(batch: TelemetryBatch)
}
