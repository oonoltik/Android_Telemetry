package com.alex.android_telemetry.telemetry.daymonitoring

import android.util.Log
import com.alex.android_telemetry.sensors.api.ActivityRecognitionSource
import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.runtime.TelemetryFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DayMonitoringManager(
    private val scope: CoroutineScope,
    private val activityRecognitionSource: ActivityRecognitionSource,
    private val telemetryFacade: TelemetryFacade,
    private val stateStore: DayMonitoringStateStore,
    private val tripGate: ActivityRecognitionTripGate = ActivityRecognitionTripGate(),
    private val onAutoStartRequested: suspend () -> Unit,
    private val onAutoStopRequested: suspend () -> Unit,
) {
    private var collectionJob: Job? = null

    fun start() {
        if (collectionJob != null) return

        scope.launch {
            syncRuntimeState()
        }

        collectionJob = activityRecognitionSource.samples
            .onEach { sample ->
                handleSample(sample)
            }
            .launchIn(scope)
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        tripGate.reset()
    }

    fun enable() {
        stateStore.setEnabled(true)
        scope.launch {
            syncRuntimeState()
        }
        Log.d("DayMonitoring", "enabled=true")
    }

    fun disable() {
        stateStore.setEnabled(false)
        stateStore.markAutoTripStopped()
        tripGate.reset()
        scope.launch {
            syncRuntimeState()
        }
        Log.d("DayMonitoring", "enabled=false")
    }

    suspend fun markAutoTripStartedFromService() {
        val currentState = telemetryFacade.observeState().value
        stateStore.markAutoTripStarted(currentState.sessionId)
        syncRuntimeState()
    }

    suspend fun markTripStoppedFromService() {
        stateStore.markAutoTripStopped()
        syncRuntimeState()
    }

    private suspend fun handleSample(sample: ActivitySample) {
        telemetryFacade.recordActivitySample(sample)

        val dmState = stateStore.load()
        if (!dmState.enabled) return

        val runtime = telemetryFacade.observeState().value
        val currentSessionId = runtime.sessionId

        val decision = tripGate.evaluate(
            sample = sample,
            tripIsActive = currentSessionId != null,
            tripWasAutoStarted = dmState.autoStartedTripActive,
            manualTripActive = isManualTripActive(
                currentSessionId = currentSessionId,
                dmState = dmState,
            ),
        )

        when (decision) {
            DayMonitoringDecision.None -> Unit

            DayMonitoringDecision.AutoStart -> {
                if (currentSessionId == null) {
                    Log.d(
                        "DayMonitoring",
                        "AUTO_START activity=${sample.dominant} confidence=${sample.confidence}"
                    )
                    onAutoStartRequested()
                    val updatedState = telemetryFacade.observeState().value
                    stateStore.markAutoTripStarted(updatedState.sessionId)
                    syncRuntimeState()
                }
            }

            DayMonitoringDecision.AutoStop -> {
                if (currentSessionId != null && dmState.autoStartedTripActive) {
                    Log.d(
                        "DayMonitoring",
                        "AUTO_STOP activity=${sample.dominant} confidence=${sample.confidence} sessionId=$currentSessionId"
                    )
                    onAutoStopRequested()
                    stateStore.markAutoTripStopped()
                    syncRuntimeState()
                }
            }
        }
    }

    private suspend fun syncRuntimeState() {
        val dmState = stateStore.load()
        telemetryFacade.setDayMonitoringState(
            enabled = dmState.enabled,
            autoTripActive = dmState.autoStartedTripActive,
            autoStartedSessionId = dmState.autoStartedSessionId,
        )
    }

    private fun isManualTripActive(
        currentSessionId: String?,
        dmState: DayMonitoringState,
    ): Boolean {
        val sessionId = currentSessionId ?: return false
        if (!dmState.autoStartedTripActive) return true
        return dmState.autoStartedSessionId != sessionId
    }
}