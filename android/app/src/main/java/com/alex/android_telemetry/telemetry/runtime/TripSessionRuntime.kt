package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.core.log.TelemetryLogger
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.capture.TelemetryCaptureCoordinator
import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode
import com.alex.android_telemetry.telemetry.domain.TripStatus
import com.alex.android_telemetry.telemetry.session.TripSessionRepository
import com.alex.android_telemetry.telemetry.session.TripSessionStarter
import com.alex.android_telemetry.telemetry.session.TripSessionStopper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class TripSessionRuntime(
    private val logger: TelemetryLogger,
    private val clockProvider: ClockProvider,
    private val runtimeStore: TripRuntimeStore,
    private val stateMachine: TripStateMachine,
    private val sessionRepository: TripSessionRepository,
    private val tripSessionStarter: TripSessionStarter,
    private val tripSessionStopper: TripSessionStopper,
    private val captureCoordinator: TelemetryCaptureCoordinator
) {
    val state: StateFlow<TripRuntimeState> = runtimeStore.state

    suspend fun startTrip(scope: CoroutineScope, deviceId: String, driverId: String?, trackingMode: TrackingMode, transportMode: TransportMode) {
        val current = runtimeStore.currentState()
        if (current.activeSession != null && current.status == TripStatus.Active) {
            logger.i("TripSessionRuntime", "startTrip ignored, session already active")
            return
        }

        runtimeStore.setState(stateMachine.transition(current, TripStatus.Starting))
        try {
            val session = tripSessionStarter.start(deviceId, driverId, trackingMode, transportMode)
            runtimeStore.update {
                it.copy(
                    status = TripStatus.Active,
                    activeSession = session,
                    lastError = null,
                    lastEndedAt = null,
                    finishPending = false
                )
            }
            captureCoordinator.start(scope, session)
        } catch (t: Throwable) {
            logger.e("TripSessionRuntime", "startTrip failed", t)
            runtimeStore.setState(stateMachine.transition(runtimeStore.currentState(), TripStatus.Error, t.message))
        }
    }

    suspend fun stopTrip(finishReason: FinishReason) {
        val current = runtimeStore.currentState()
        if (current.activeSession == null) return
        if (!stateMachine.canTransition(current.status, TripStatus.Stopping)) return

        runtimeStore.setState(stateMachine.transition(current, TripStatus.Stopping))
        try {
            captureCoordinator.stop(flushRemaining = true)
            runtimeStore.setState(stateMachine.transition(runtimeStore.currentState(), TripStatus.Finishing))
            tripSessionStopper.stop(finishReason)
            runtimeStore.update {
                it.copy(
                    status = TripStatus.Finished,
                    activeSession = null,
                    counters = it.counters.copy(samplesBuffered = 0)
                )
            }
        } catch (t: Throwable) {
            logger.e("TripSessionRuntime", "stopTrip failed", t)
            runtimeStore.setState(stateMachine.transition(runtimeStore.currentState(), TripStatus.Error, t.message))
        }
    }

    suspend fun recoverIfNeeded(scope: CoroutineScope) {
        val existing = sessionRepository.getActiveSession() ?: return
        runtimeStore.update { it.copy(status = TripStatus.Recovery, activeSession = existing, lastError = null) }
        captureCoordinator.start(scope, existing)
        runtimeStore.update { it.copy(status = TripStatus.Active) }
    }

    fun snapshot(): TripRuntimeSnapshot {
        val current = runtimeStore.currentState()
        val elapsedSec = current.activeSession?.let {
            ((clockProvider.nowEpochMillis() - it.startedAtEpochMillis).coerceAtLeast(0L) / 1000L)
        } ?: current.counters.elapsedSec
        return current.copy(counters = current.counters.copy(elapsedSec = elapsedSec)).toSnapshot()
    }
}
