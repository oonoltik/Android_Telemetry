package com.alex.android_telemetry.telemetry.runtime

import android.util.Log
import com.alex.android_telemetry.sensors.api.AccelerometerSource
import com.alex.android_telemetry.sensors.api.DeviceStateSource
import com.alex.android_telemetry.sensors.api.GyroscopeSource
import com.alex.android_telemetry.sensors.api.HeadingSource
import com.alex.android_telemetry.sensors.api.LocationSource
import com.alex.android_telemetry.sensors.api.NetworkStateSource

import com.alex.android_telemetry.telemetry.batching.TelemetryBatchBuilder
import com.alex.android_telemetry.telemetry.batching.TelemetryFrameAssembler
import com.alex.android_telemetry.telemetry.detectors.AccelEventDetector
import com.alex.android_telemetry.telemetry.detectors.BrakeEventDetector
import com.alex.android_telemetry.telemetry.detectors.MotionVectorComputer
import com.alex.android_telemetry.telemetry.detectors.RoadAnomalyDetector
import com.alex.android_telemetry.telemetry.detectors.TelemetryEventDetector
import com.alex.android_telemetry.telemetry.detectors.TurnEventDetector
import com.alex.android_telemetry.telemetry.domain.TripRepository
import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.telemetry.domain.policy.EventThresholdResolver
import com.alex.android_telemetry.telemetry.ingest.TelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.trips.api.ClientAggDto
import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.batching.LegacyBatchSequenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.alex.android_telemetry.telemetry.domain.TripFinishResult
import com.alex.android_telemetry.telemetry.domain.model.TripFinishUiState

import com.alex.android_telemetry.telemetry.domain.model.AltimeterSample
import com.alex.android_telemetry.telemetry.domain.model.PedometerSample
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionSample

import com.alex.android_telemetry.sensors.api.ActivityRecognitionSource
import com.alex.android_telemetry.sensors.api.AltimeterSource
import com.alex.android_telemetry.sensors.api.PedometerSource
import com.alex.android_telemetry.sensors.api.ScreenInteractionSource

interface TripRuntimeStateStore {
    suspend fun save(state: TripRuntimeState)
    suspend fun restore(): TripRuntimeState?
    suspend fun clear()
}

class InMemoryTripRuntimeStateStore : TripRuntimeStateStore {
    private var state: TripRuntimeState? = null
    override suspend fun save(state: TripRuntimeState) { this.state = state }
    override suspend fun restore(): TripRuntimeState? = state
    override suspend fun clear() { state = null }
}

sealed interface TripLifecycleDecision {
    data object StartCollection : TripLifecycleDecision
    data object ContinueCollection : TripLifecycleDecision
    data object PauseCollection : TripLifecycleDecision
    data object FinishTrip : TripLifecycleDecision
    data object Ignore : TripLifecycleDecision
}
class LegacyTripStateMachine {
    fun start(mode: TrackingMode, now: Instant): TripRuntimeState = TripRuntimeState(
        sessionId = java.util.UUID.randomUUID().toString(),
        trackingMode = mode,
        telemetryMode = TelemetryMode.COLLECTING,
        startedAt = now,
        lastSampleAt = now,
        isForegroundCollection = true,
    )

    fun pause(state: TripRuntimeState): TripRuntimeState =
        state.copy(telemetryMode = TelemetryMode.PAUSED)

    fun resume(state: TripRuntimeState): TripRuntimeState =
        state.copy(telemetryMode = TelemetryMode.COLLECTING)

    fun finish(state: TripRuntimeState, now: Instant): TripRuntimeState =
        state.copy(
            telemetryMode = TelemetryMode.FINISHING,
            pendingFinish = false,
            finishUiState = TripFinishUiState.FINISHING_IN_PROGRESS,
            lastFinishError = null,
            lastTripReport = null,
            lastSampleAt = now,
        )

    fun stop(): TripRuntimeState = TripRuntimeState()
}


class TelemetryOrchestrator(
    private val scope: CoroutineScope,
    private val deviceIdProvider: () -> String,
    private val driverIdProvider: () -> String?,
    private val transportModeProvider: () -> String?,
    private val tripRepository: TripRepository,
    private val accelerometerSource: AccelerometerSource,
    private val gyroscopeSource: GyroscopeSource,
    private val locationSource: LocationSource,
    private val headingSource: HeadingSource?,
    private val deviceStateSource: DeviceStateSource,
    private val networkStateSource: NetworkStateSource,
    private val activityRecognitionSource: ActivityRecognitionSource,
    private val pedometerSource: PedometerSource,
    private val altimeterSource: AltimeterSource,
    private val screenInteractionSource: ScreenInteractionSource,
    private val thresholdResolver: EventThresholdResolver,
    private val frameAssembler: TelemetryFrameAssembler,
    private val motionVectorComputer: MotionVectorComputer,
    private val batchBuilder: TelemetryBatchBuilder,

    private val batchEnqueuer: TelemetryBatchEnqueuer,
    private val outboxRepository: com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository,
    private val tripDeliveryStatsStore: com.alex.android_telemetry.telemetry.trips.storage.TripDeliveryStatsStore,
    private val runtimeStateStore: TripRuntimeStateStore,
    private val stateMachine: LegacyTripStateMachine = LegacyTripStateMachine(),
    private val batchSequenceStore: LegacyBatchSequenceStore,
) {
    private val detectors: List<TelemetryEventDetector> = listOf(
        AccelEventDetector { thresholdResolver.getEffectiveThresholds() },
        BrakeEventDetector { thresholdResolver.getEffectiveThresholds() },
        TurnEventDetector { thresholdResolver.getEffectiveThresholds() },
        RoadAnomalyDetector { thresholdResolver.getEffectiveThresholds() },
    )

    private val mutableState = MutableStateFlow(TripRuntimeState())
    val state: StateFlow<TripRuntimeState> = mutableState.asStateFlow()

    private var latestAccel: ImuSample? = null
    private var latestGyro: ImuSample? = null
    private var latestLocation: LocationFix? = null
    private var latestHeading: HeadingSample? = null
    private var latestDeviceState: DeviceStateSnapshot? = null
    private var latestNetworkState: NetworkStateSnapshot? = null
    private var latestActivity: ActivitySample? = null

    private var latestPedometer: PedometerSample? = null
    private var latestAltimeter: AltimeterSample? = null
    private var latestScreenInteraction: ScreenInteractionSample? = null

    private var collectionJob: Job? = null
    private val flushMutex = Mutex()

    private suspend fun shouldResumeCollectedSession(
        restoredState: TripRuntimeState,
        now: Instant,
    ): Boolean {
        val sessionId = restoredState.sessionId
        val startedAt = restoredState.startedAt

        if (sessionId.isNullOrBlank()) {
            Log.d("TelemetryTrip", "restoreGuard(): reject, missing sessionId")
            return false
        }

        if (restoredState.telemetryMode != TelemetryMode.COLLECTING) {
            Log.d(
                "TelemetryTrip",
                "restoreGuard(): reject, telemetryMode=${restoredState.telemetryMode} sessionId=$sessionId"
            )
            return false
        }

        if (startedAt == null) {
            Log.d("TelemetryTrip", "restoreGuard(): reject, missing startedAt sessionId=$sessionId")
            return false
        }

        val ageSec =
            ((now.toEpochMilliseconds() - startedAt.toEpochMilliseconds()).coerceAtLeast(0L)) / 1000.0

        val deliveredBatches = runCatching {
            tripDeliveryStatsStore.get(sessionId).deliveredBatches
        }.getOrElse {
            Log.e("TelemetryTrip", "restoreGuard(): deliveryStats read failed sessionId=$sessionId", it)
            0
        }

        val sessionQueued = runCatching {
            outboxRepository.countUndeliveredForSession(sessionId)
        }.getOrElse {
            Log.e("TelemetryTrip", "restoreGuard(): outbox count failed sessionId=$sessionId", it)
            0
        }

        Log.d(
            "TelemetryTrip",
            "restoreGuard(): inspect sessionId=$sessionId ageSec=$ageSec deliveredBatches=$deliveredBatches sessionQueued=$sessionQueued"
        )

        if (ageSec > MAX_RESTORE_SESSION_AGE_SEC) {
            Log.d(
                "TelemetryTrip",
                "restoreGuard(): reject, too old sessionId=$sessionId ageSec=$ageSec"
            )
            return false
        }

        if (deliveredBatches <= 0 && sessionQueued <= 0) {
            Log.d(
                "TelemetryTrip",
                "restoreGuard(): reject, no delivered and no queued data sessionId=$sessionId"
            )
            return false
        }

        Log.d(
            "TelemetryTrip",
            "restoreGuard(): accept sessionId=$sessionId deliveredBatches=$deliveredBatches sessionQueued=$sessionQueued"
        )
        return true
    }

    private suspend fun detachFromStaleActiveSession(restoredState: TripRuntimeState) {
        val sessionId = restoredState.sessionId

        Log.w(
            "TelemetryTrip",
            "restoreGuard(): detaching from stale active runtime sessionId=$sessionId"
        )

        mutableState.emit(stateMachine.stop())
        runtimeStateStore.clear()
        batchSequenceStore.reset()

        collectionJob?.cancel()
        collectionJob = null

        accelerometerSource.stop()
        gyroscopeSource.stop()
        locationSource.stop()
        headingSource?.stop()

        val sessionQueued = runCatching {
            if (sessionId.isNullOrBlank()) 0 else outboxRepository.countUndeliveredForSession(sessionId)
        }.getOrDefault(0)

        Log.d(
            "TelemetryTrip",
            "restoreGuard(): runtime detached only, queued batches preserved sessionId=$sessionId sessionQueued=$sessionQueued"
        )
    }

    private suspend fun logOutboxBeforeNewTripStart() {
        val totalQueued = runCatching {
            outboxRepository.countAll()
        }.getOrElse {
            Log.e("TelemetryTrip", "startTrip(): failed to read totalQueued", it)
            -1
        }

        Log.d(
            "TelemetryTrip",
            "startTrip(): proceeding even with old outbox tail totalQueued=$totalQueued"
        )
    }

    suspend fun restore(now: Instant = kotlinx.datetime.Clock.System.now()) {
        val restoredState = runtimeStateStore.restore()

        if (restoredState == null) {
            Log.d("TelemetryTrip", "restore(): nothing to restore")
            return
        }

        Log.d(
            "TelemetryTrip",
            "restore(): found state telemetryMode=${restoredState.telemetryMode} sessionId=${restoredState.sessionId} startedAt=${restoredState.startedAt}"
        )

        if (restoredState.sessionId != null && restoredState.telemetryMode == TelemetryMode.COLLECTING) {
            val shouldResume = shouldResumeCollectedSession(
                restoredState = restoredState,
                now = now,
            )

            if (!shouldResume) {
                detachFromStaleActiveSession(restoredState)
                Log.d(
                    "TelemetryTrip",
                    "restore(): skipped auto resume for sessionId=${restoredState.sessionId}, queued batches kept"
                )
                return
            }

            mutableState.emit(restoredState)
            startSourcesIfNeeded()
            subscribeIfNeeded()

            Log.d(
                "TelemetryTrip",
                "restore(): resumed collecting sessionId=${restoredState.sessionId}"
            )
            return
        }

        mutableState.emit(restoredState)
        Log.d(
            "TelemetryTrip",
            "restore(): restored passive state=${restoredState.telemetryMode} sessionId=${restoredState.sessionId}"
        )
    }

    suspend fun startTrip(
        mode: TrackingMode = TrackingMode.SINGLE_TRIP,
        now: Instant = kotlinx.datetime.Clock.System.now(),
    ) {
        val current = state.value

        if (current.sessionId != null && current.telemetryMode == TelemetryMode.COLLECTING) {
            Log.d(
                "TelemetryTrip",
                "startTrip(): ignored, already collecting sessionId=${current.sessionId}"
            )
            return
        }

        val previousSessionId = current.sessionId
        val previousMode = current.telemetryMode

        val previousSessionQueued = runCatching {
            previousSessionId?.let { outboxRepository.countUndeliveredForSession(it) } ?: 0
        }.getOrElse {
            Log.e(
                "TelemetryTrip",
                "startTrip(): failed to read previous session queued sessionId=$previousSessionId",
                it
            )
            -1
        }

        Log.d(
            "TelemetryTrip",
            "startTrip(): requested with previousSessionId=$previousSessionId previousTelemetryMode=$previousMode previousSessionQueued=$previousSessionQueued"
        )

        logOutboxBeforeNewTripStart()

        latestAccel = null
        latestGyro = null
        latestLocation = null
        latestHeading = null
        latestDeviceState = null
        latestNetworkState = null
        latestActivity = null
        latestPedometer = null
        latestAltimeter = null
        latestScreenInteraction = null

        batchBuilder.resetWindow()

        batchSequenceStore.reset()

        val started = stateMachine.start(mode, now)
        mutableState.emit(started)
        runtimeStateStore.save(started)

        startSourcesIfNeeded()
        subscribeIfNeeded()

        Log.d(
            "TelemetryTrip",
            "startTrip(): started new sessionId=${started.sessionId} replacingPreviousSessionId=$previousSessionId oldSessionQueued=$previousSessionQueued"
        )

        scope.launch {
            runCatching {
                val startedSessionId = started.sessionId ?: return@runCatching
                val totalQueued = outboxRepository.countAll()
                val sessionQueued = outboxRepository.countUndeliveredForSession(startedSessionId)

                Log.d(
                    "TelemetryTrip",
                    "outbox@start sessionId=$startedSessionId totalQueued=$totalQueued sessionQueued=$sessionQueued"
                )
            }.onFailure {
                Log.e("TelemetryTrip", "outbox@start failed", it)
            }
        }
    }

    suspend fun stopTrip(now: Instant = kotlinx.datetime.Clock.System.now()) {
        Log.d("TelemetryTrip", "stopTrip() CALLED")

        val current = state.value
        val sessionId = current.sessionId
        val driverId = driverIdProvider()?.trim().orEmpty()
        val deviceId = deviceIdProvider()
        val transportMode = transportModeProvider()

        if (sessionId.isNullOrBlank()) {
            Log.d("TelemetryTrip", "stopTrip(): ignored, no active session")
            return
        }

        Log.d(
            "TelemetryTrip",
            "stopTrip(): sessionId=$sessionId driverId=$driverId deviceId=$deviceId trackingMode=${current.trackingMode} transportMode=$transportMode"
        )

        val finishingState = stateMachine.finish(current, now)
        mutableState.emit(finishingState)
        runtimeStateStore.save(finishingState)

        Log.d("TelemetryTrip", "stopTrip(): flushing current buffers sessionId=$sessionId")
        flushNow(now)

        Log.d("TelemetryTrip", "stopTrip(): stopping sensors sessionId=$sessionId")
        accelerometerSource.stop()
        gyroscopeSource.stop()
        locationSource.stop()
        headingSource?.stop()
        activityRecognitionSource.stop()
        pedometerSource.stop()
        altimeterSource.stop()
        screenInteractionSource.stop()

        latestActivity = null
        latestPedometer = null
        latestAltimeter = null
        latestScreenInteraction = null

        var finalUiState = TripFinishUiState.IDLE
        var finalPendingFinish = false
        var finalReport: com.alex.android_telemetry.telemetry.trips.api.TripReportDto? = null
        var finalError: String? = null

        if (driverId.isNotBlank()) {
            val tripDurationSec = current.startedAt?.let { startedAt ->
                ((now.toEpochMilliseconds() - startedAt.toEpochMilliseconds()).coerceAtLeast(0L)) / 1000.0
            }

            val clientMetrics = buildClientTripMetrics(current)

            Log.d(
                "TelemetryTrip",
                "stopTrip(): dispatch finish sessionId=$sessionId tripDurationSec=$tripDurationSec"
            )

            val command = com.alex.android_telemetry.telemetry.trips.api.FinishCommand(
                sessionId = sessionId,
                driverId = driverId,
                deviceId = deviceId,
                clientEndedAt = now.toString(),
                trackingMode = current.trackingMode?.toWireValue(),
                transportMode = transportMode,
                tripDurationSec = tripDurationSec,
                finishReason = "app_stop",
                clientMetrics = clientMetrics,
                tripSummary = null,
                tripMetricsRaw = null,
                deviceContext = null,
                tailActivityContext = null,
            )

            when (val finishResult = tripRepository.finishTrip(command)) {
                is TripFinishResult.Sent -> {
                    finalUiState = TripFinishUiState.FINISHED_WITH_REPORT
                    finalPendingFinish = false
                    finalReport = finishResult.report
                    finalError = null

                    Log.d(
                        "TelemetryTrip",
                        "stopTrip(): finish sent sessionId=$sessionId reportSessionId=${finishResult.report.sessionId}"
                    )
                }

                is TripFinishResult.Queued -> {
                    finalUiState = TripFinishUiState.FINISH_QUEUED
                    finalPendingFinish = true
                    finalReport = finishResult.placeholderReport
                    finalError = finishResult.reason

                    Log.w(
                        "TelemetryTrip",
                        "stopTrip(): finish queued sessionId=$sessionId reason=${finishResult.reason}"
                    )
                }

                is TripFinishResult.Failed -> {
                    finalUiState = TripFinishUiState.FINISH_FAILED
                    finalPendingFinish = false
                    finalReport = null
                    finalError = finishResult.message

                    Log.e(
                        "TelemetryTrip",
                        "stopTrip(): finish failed sessionId=$sessionId error=${finishResult.message}",
                        finishResult.error
                    )
                }
            }
        } else {
            finalUiState = TripFinishUiState.FINISH_FAILED
            finalPendingFinish = false
            finalError = "driver_id missing"

            Log.w("TelemetryTrip", "stopTrip(): finish skipped, driverId missing sessionId=$sessionId")
        }

        Log.d("TelemetryTrip", "stopTrip(): resetting batch sequence sessionId=$sessionId")
        batchSequenceStore.reset()

        val stoppedState = stateMachine.stop().copy(
            pendingFinish = finalPendingFinish,
            finishUiState = finalUiState,
            lastTripReport = finalReport,
            lastFinishError = finalError,
        )

        mutableState.emit(stoppedState)
        runtimeStateStore.clear()

        collectionJob?.cancel()
        collectionJob = null

        Log.d(
            "TelemetryTrip",
            "stopTrip(): completed sessionId=$sessionId finishUiState=$finalUiState pendingFinish=$finalPendingFinish"
        )
    }
    suspend fun pauseCollection() {
        val updated = stateMachine.pause(state.value)
        mutableState.emit(updated)
        runtimeStateStore.save(updated)
    }

    suspend fun resumeCollection() {
        val updated = stateMachine.resume(state.value)
        mutableState.emit(updated)
        runtimeStateStore.save(updated)
    }

    suspend fun flushNow(now: Instant = kotlinx.datetime.Clock.System.now()) {
        flushMutex.withLock {
            val current = state.value
            val sessionId = current.sessionId ?: return

            val batch = batchBuilder.flush(
                deviceId = deviceIdProvider(),
                driverId = driverIdProvider(),
                sessionId = sessionId,
                trackingMode = current.trackingMode,
                transportMode = transportModeProvider(),
                latestDeviceState = latestDeviceState,
                latestNetworkState = latestNetworkState,
                headingSummary = latestHeading,
                activitySummary = latestActivity,
                thresholds = thresholdResolver.getEffectiveThresholds(),
                now = now,
            ) ?: return

            Log.d(
                "TelemetryTrip",
                "flushNow(): enqueue batch sessionId=${batch.sessionId} batchId=${batch.batchId} batchSeq=${batch.batchSeq} frames=${batch.frames.size} events=${batch.events.size} motionActivity=${batch.motionActivitySummary != null} activityContext=${batch.activityContextSummary != null} pedometer=${batch.pedometerSummary != null} altimeter=${batch.altimeterSummary != null} screen=${batch.screenInteractionContextSummary != null}"
            )

            batchEnqueuer.enqueue(batch)
        }
    }

    suspend fun recordActivitySample(sample: ActivitySample) {
        latestActivity = sample
        batchBuilder.addActivitySample(sample)

        val updated = state.value.copy(lastSampleAt = sample.timestamp)
        mutableState.emit(updated)
        runtimeStateStore.save(updated)
    }

    suspend fun recordPedometerSample(sample: PedometerSample) {
        latestPedometer = sample
        batchBuilder.addPedometerSample(sample)
    }

    suspend fun recordAltimeterSample(sample: AltimeterSample) {
        latestAltimeter = sample
        batchBuilder.addAltimeterSample(sample)
    }

    suspend fun recordScreenInteractionSample(sample: ScreenInteractionSample) {
        latestScreenInteraction = sample
        batchBuilder.addScreenInteractionSample(sample)
    }

    private suspend fun startSourcesIfNeeded() {
        accelerometerSource.start()
        gyroscopeSource.start()
        locationSource.start()
        headingSource?.start()
        activityRecognitionSource.start()
        pedometerSource.start()
        altimeterSource.start()
        screenInteractionSource.start()
    }

    private fun subscribeIfNeeded() {
        if (collectionJob != null) return

        collectionJob = scope.launch {
            accelerometerSource.samples.onEach { sample ->
                latestAccel = latestAccel.merge(sample)
                onSensorTick(sample.timestamp)
            }.launchIn(this)

            gyroscopeSource.samples.onEach { sample ->
                latestGyro = latestGyro.merge(sample)
                onSensorTick(sample.timestamp)
            }.launchIn(this)

            locationSource.fixes.onEach { fix ->
                updateDistance(fix)
                latestLocation = fix
                onSensorTick(fix.timestamp)
            }.launchIn(this)

            headingSource?.samples?.onEach { sample ->
                latestHeading = sample
            }?.launchIn(this)

            deviceStateSource.snapshots.onEach { latestDeviceState = it }.launchIn(this)
            networkStateSource.snapshots.onEach { latestNetworkState = it }.launchIn(this)

            activityRecognitionSource.samples.onEach { sample ->
                recordActivitySample(sample)
            }.launchIn(this)

            pedometerSource.samples.onEach { sample ->
                recordPedometerSample(sample)
            }.launchIn(this)

            altimeterSource.samples.onEach { sample ->
                recordAltimeterSample(sample)
            }.launchIn(this)

            screenInteractionSource.samples.onEach { sample ->
                recordScreenInteractionSample(sample)
            }.launchIn(this)
        }
    }

    private suspend fun onSensorTick(now: Instant) {
        val current = state.value
        if (current.telemetryMode != TelemetryMode.COLLECTING) return

        val imu = mergeImu(latestAccel, latestGyro)
        val motion = motionVectorComputer.compute(imu, latestLocation)

        detectors.forEach { detector ->
            detector.detect(motion, now)?.let { event ->
                batchBuilder.addEvent(event)
                mutableState.emit(state.value.copy(lastEventAt = event.timestamp))
            }
        }

        val frame = frameAssembler.assemble(
            timestamp = now,
            location = latestLocation,
            imu = imu,
            heading = latestHeading,
            deviceState = latestDeviceState,
            networkState = latestNetworkState,
            motionVector = motion,
        )
        batchBuilder.addFrame(frame)

        val updated = state.value.copy(
            lastSampleAt = now,
            lastLocationAt = latestLocation?.timestamp ?: state.value.lastLocationAt,
        )
        mutableState.emit(updated)
        runtimeStateStore.save(updated)

        if (batchBuilder.shouldFlush(now)) {
            Log.d(
                "TelemetryTrip",
                "onSensorTick(): shouldFlush=true sessionId=${current.sessionId} now=$now"
            )
            flushNow(now)
        }
    }

    private fun updateDistance(fix: LocationFix) {
        val previous = latestLocation ?: return
        val segmentMeters = haversineMeters(previous.lat, previous.lon, fix.lat, fix.lon)
        val updated = state.value.copy(distanceM = state.value.distanceM + segmentMeters)
        mutableState.value = updated
    }

    private fun mergeImu(accel: ImuSample?, gyro: ImuSample?): ImuSample? {
        if (accel == null && gyro == null) return null
        val ts = accel?.timestamp ?: gyro!!.timestamp
        return ImuSample(
            timestamp = ts,
            accelX = accel?.accelX,
            accelY = accel?.accelY,
            accelZ = accel?.accelZ,
            gyroX = gyro?.gyroX,
            gyroY = gyro?.gyroY,
            gyroZ = gyro?.gyroZ,
        )
    }
}

class TelemetryFacade(
    private val orchestrator: TelemetryOrchestrator,
) {
    fun observeState(): StateFlow<TripRuntimeState> = orchestrator.state

    suspend fun restore() = orchestrator.restore()
    suspend fun startTrip() = orchestrator.startTrip()
    suspend fun stopTrip() = orchestrator.stopTrip()
    suspend fun pauseCollection() = orchestrator.pauseCollection()
    suspend fun resumeCollection() = orchestrator.resumeCollection()
    suspend fun flushNow() = orchestrator.flushNow()

    suspend fun recordActivitySample(sample: ActivitySample) =
        orchestrator.recordActivitySample(sample)

    suspend fun recordPedometerSample(sample: PedometerSample) =
        orchestrator.recordPedometerSample(sample)

    suspend fun recordAltimeterSample(sample: AltimeterSample) =
        orchestrator.recordAltimeterSample(sample)

    suspend fun recordScreenInteractionSample(sample: ScreenInteractionSample) =
        orchestrator.recordScreenInteractionSample(sample)
}

private fun ImuSample?.merge(newValue: ImuSample): ImuSample =
    ImuSample(
        timestamp = newValue.timestamp,
        accelX = newValue.accelX ?: this?.accelX,
        accelY = newValue.accelY ?: this?.accelY,
        accelZ = newValue.accelZ ?: this?.accelZ,
        gyroX = newValue.gyroX ?: this?.gyroX,
        gyroY = newValue.gyroY ?: this?.gyroY,
        gyroZ = newValue.gyroZ ?: this?.gyroZ,
    )

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

private fun buildClientTripMetrics(state: TripRuntimeState): ClientTripMetricsDto {
    val distanceKm = state.distanceM / 1000.0

    val zeroAgg = ClientAggDto(
        count = 0,
        sumIntensity = 0.0,
        maxIntensity = 0.0,
        countPerKm = 0.0,
        sumPerKm = 0.0,
    )

    return ClientTripMetricsDto(
        tripDistanceM = state.distanceM,
        tripDistanceKmFromGps = distanceKm,
        brake = zeroAgg,
        accel = zeroAgg,
        road = zeroAgg,
        turn = zeroAgg,
    )
}

private const val MAX_RESTORE_SESSION_AGE_SEC = 60 * 60.0

private fun TrackingMode.toWireValue(): String = when (this) {
    TrackingMode.SINGLE_TRIP -> "single_trip"
    TrackingMode.DAY_MONITORING -> "day_monitoring"
}