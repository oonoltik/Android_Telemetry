package com.alex.android_telemetry.telemetry.capture

import com.alex.android_telemetry.core.log.TelemetryLogger
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.batching.BatchFlushPolicy
import com.alex.android_telemetry.telemetry.batching.BatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchAssembler
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchWindow
import com.alex.android_telemetry.telemetry.capture.location.LocationTelemetrySource
import com.alex.android_telemetry.telemetry.integration.DeliveryFacade
import com.alex.android_telemetry.telemetry.integration.DeliveryRoute
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeStore
import com.alex.android_telemetry.telemetry.session.TripSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TelemetryCaptureCoordinator(
    private val clockProvider: ClockProvider,
    private val logger: TelemetryLogger,
    private val runtimeStore: TripRuntimeStore,
    private val locationTelemetrySource: LocationTelemetrySource,
    private val batchWindow: TelemetryBatchWindow,
    private val batchFlushPolicy: BatchFlushPolicy,
    private val batchSequenceStore: BatchSequenceStore,
    private val batchAssembler: TelemetryBatchAssembler,
    private val deliveryFacade: DeliveryFacade
) {
    private var collectionJob: Job? = null
    private var activeSession: TripSession? = null
    private var lastFlushElapsedMs: Long = 0L

    suspend fun start(scope: CoroutineScope, session: TripSession) {
        activeSession = session
        batchWindow.clear()
        lastFlushElapsedMs = clockProvider.elapsedRealtimeMillis()
        locationTelemetrySource.start()
        collectionJob = scope.launch {
            locationTelemetrySource.observeSamples().collect { sample ->
                batchWindow.append(sample)
                runtimeStore.update { state -> state.copy(counters = state.counters.copy(samplesBuffered = batchWindow.size())) }
                val elapsed = clockProvider.elapsedRealtimeMillis() - lastFlushElapsedMs
                if (
                    batchWindow.size() >= batchFlushPolicy.maxFrames ||
                    elapsed >= batchFlushPolicy.maxWindowMs
                ) {
                    flushNow()
                }
            }
        }
    }

    suspend fun stop(flushRemaining: Boolean) {
        if (flushRemaining) flushNow() else batchWindow.clear()
        collectionJob?.cancelAndJoin()
        collectionJob = null
        locationTelemetrySource.stop()
        activeSession = null
    }

    suspend fun flushNow() {
        val session = activeSession ?: return
        val samples = batchWindow.drain()
        if (samples.isEmpty()) return

        val batchSeq = batchSequenceStore.next(session.sessionId)
        val batch = batchAssembler.assemble(session, batchSeq, samples)
        lastFlushElapsedMs = clockProvider.elapsedRealtimeMillis()

        runtimeStore.update {
            state -> state.copy(
                counters = state.counters.copy(
                    samplesBuffered = 0,
                    batchesCreated = state.counters.batchesCreated + 1
                )
            )
        }

        val result = deliveryFacade.enqueueOrSend(batch)
        if (result.delivered) {
            runtimeStore.update { state ->
                val routeStats = when (result.route) {
                    DeliveryRoute.EU -> state.routeStats.copy(euDelivered = state.routeStats.euDelivered + 1)
                    DeliveryRoute.RU -> state.routeStats.copy(ruDelivered = state.routeStats.ruDelivered + 1)
                    null -> state.routeStats
                }
                state.copy(
                    counters = state.counters.copy(batchesDelivered = state.counters.batchesDelivered + 1),
                    routeStats = routeStats
                )
            }
        } else if (result.error != null) {
            logger.w("CaptureCoordinator", "batch delivery incomplete: ${result.error}")
        }
    }

    fun isRunning(): Boolean = collectionJob?.isActive == true
    fun currentJob(): Job? = collectionJob
}
