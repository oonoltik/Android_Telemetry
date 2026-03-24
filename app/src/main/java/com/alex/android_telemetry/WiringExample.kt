package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.batching.BatchIdGenerator
import com.alex.android_telemetry.telemetry.batching.BatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchBuilder
import com.alex.android_telemetry.telemetry.batching.TelemetryFrameAssembler
import com.alex.android_telemetry.telemetry.detectors.MotionVectorComputer
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.policy.BatchFlushPolicy
import com.alex.android_telemetry.telemetry.domain.policy.EventThresholdResolver
import com.alex.android_telemetry.telemetry.ingest.TelemetryBatchEnqueuer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class StaticThresholdResolver : EventThresholdResolver {
    override fun getEffectiveThresholds(): EventThresholdSet = EventThresholdSet(
        accelSharpG = 0.18,
        accelEmergencyG = 0.28,
        brakeSharpG = 0.22,
        brakeEmergencyG = 0.32,
        turnSharpG = 0.22,
        turnEmergencyG = 0.30,
        roadLowG = 0.45,
        roadHighG = 0.75,
    )
}

class LoggingBatchEnqueuer : TelemetryBatchEnqueuer {
    override suspend fun enqueue(batch: com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch) {
        println("ENQUEUE batch=${'$'}{batch.batchId} seq=${'$'}{batch.batchSeq} frames=${'$'}{batch.frames.size} events=${'$'}{batch.events.size}")
    }
}

fun buildTelemetryFacade(): TelemetryFacade {
    val scope = CoroutineScope(Dispatchers.Default)
    val orchestrator = TelemetryOrchestrator(
        scope = scope,
        deviceIdProvider = { "android-device-id" },
        driverIdProvider = { "driver-123" },
        transportModeProvider = { "car" },
        accelerometerSource = StubAccelerometerSource(),
        gyroscopeSource = StubGyroscopeSource(),
        locationSource = StubLocationSource(),
        headingSource = StubHeadingSource(),
        deviceStateSource = StubDeviceStateSource(),
        networkStateSource = StubNetworkStateSource(),
        thresholdResolver = StaticThresholdResolver(),
        frameAssembler = TelemetryFrameAssembler(),
        motionVectorComputer = MotionVectorComputer(),
        batchBuilder = TelemetryBatchBuilder(
            flushPolicy = BatchFlushPolicy(maxWindowMs = 10_000, maxFrames = 50),
            batchSequenceStore = BatchSequenceStore(),
            batchIdGenerator = BatchIdGenerator(),
        ),
        batchEnqueuer = LoggingBatchEnqueuer(),
        runtimeStateStore = InMemoryTripRuntimeStateStore(),
    )
    return TelemetryFacade(orchestrator)
}
