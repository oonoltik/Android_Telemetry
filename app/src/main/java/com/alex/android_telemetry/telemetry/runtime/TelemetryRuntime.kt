package com.alex.android_telemetry.telemetry.runtime

import kotlinx.datetime.Clock

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
import kotlinx.datetime.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

class TripStateMachine {
    fun start(mode: TrackingMode, now: Instant): TripRuntimeState = TripRuntimeState(
        sessionId = UUID.randomUUID().toString(),
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
        state.copy(telemetryMode = TelemetryMode.FINISHING, pendingFinish = true, lastSampleAt = now)

    fun stop(): TripRuntimeState = TripRuntimeState()
}

class TelemetryOrchestrator(
    private val scope: CoroutineScope,
    private val deviceIdProvider: () -> String,
    private val driverIdProvider: () -> String?,
    private val transportModeProvider: () -> String?,
    private val accelerometerSource: AccelerometerSource,
    private val gyroscopeSource: GyroscopeSource,
    private val locationSource: LocationSource,
    private val headingSource: HeadingSource?,
    private val deviceStateSource: DeviceStateSource,
    private val networkStateSource: NetworkStateSource,
    private val thresholdResolver: EventThresholdResolver,
    private val frameAssembler: TelemetryFrameAssembler,
    private val motionVectorComputer: MotionVectorComputer,
    private val batchBuilder: TelemetryBatchBuilder,
    private val batchEnqueuer: TelemetryBatchEnqueuer,
    private val runtimeStateStore: TripRuntimeStateStore,
    private val stateMachine: TripStateMachine = TripStateMachine(),
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
    private var collectionJob: Job? = null

    suspend fun restore() {
        runtimeStateStore.restore()?.let { restoredState ->
            mutableState.emit(restoredState)
        }
    }

    suspend fun startTrip(mode: TrackingMode = TrackingMode.SINGLE_TRIP, now: Instant = kotlinx.datetime.Clock.System.now()) {
        val started = stateMachine.start(mode, now)
        mutableState.emit(started)
        runtimeStateStore.save(started)
        startSourcesIfNeeded()
        subscribeIfNeeded()
    }

    suspend fun stopTrip(now: Instant = kotlinx.datetime.Clock.System.now()) {
        flushNow(now)
        accelerometerSource.stop()
        gyroscopeSource.stop()
        locationSource.stop()
        headingSource?.stop()
        mutableState.emit(stateMachine.stop())
        runtimeStateStore.clear()
        collectionJob?.cancel()
        collectionJob = null
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
        batchEnqueuer.enqueue(batch)
    }

    private suspend fun startSourcesIfNeeded() {
        accelerometerSource.start()
        gyroscopeSource.start()
        locationSource.start()
        headingSource?.start()
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
                latestLocation = fix
                updateDistance(fix)
                onSensorTick(fix.timestamp)
            }.launchIn(this)

            headingSource?.samples?.onEach { sample ->
                latestHeading = sample
            }?.launchIn(this)

            deviceStateSource.snapshots.onEach { latestDeviceState = it }.launchIn(this)
            networkStateSource.snapshots.onEach { latestNetworkState = it }.launchIn(this)
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

    suspend fun startTrip() = orchestrator.startTrip()
    suspend fun stopTrip() = orchestrator.stopTrip()
    suspend fun pauseCollection() = orchestrator.pauseCollection()
    suspend fun resumeCollection() = orchestrator.resumeCollection()
    suspend fun flushNow() = orchestrator.flushNow()
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
