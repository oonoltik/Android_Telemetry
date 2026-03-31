package com.alex.android_telemetry.telemetry.batching

import com.alex.android_telemetry.core.id.BatchIdFactory
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.model.TelemetryBatchDraft
import com.alex.android_telemetry.telemetry.model.TelemetrySampleDraft
import com.alex.android_telemetry.telemetry.session.TripSession

class TelemetryBatchAssembler(
    private val clockProvider: ClockProvider,
    private val batchIdFactory: BatchIdFactory
) {
    fun assemble(session: TripSession, batchSeq: Int, samples: List<TelemetrySampleDraft>): TelemetryBatchDraft {
        return TelemetryBatchDraft(
            deviceId = session.deviceId,
            driverId = session.driverId,
            sessionId = session.sessionId,
            timestamp = clockProvider.nowIsoStringUtc(),
            batchId = batchIdFactory.create(session.sessionId, batchSeq),
            batchSeq = batchSeq,
            trackingMode = session.trackingMode,
            transportMode = session.transportMode,
            samples = samples
        )
    }
}
