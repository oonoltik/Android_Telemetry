package com.alex.android_telemetry.telemetry.batching

import android.content.Context
import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.MotionVector
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.domain.policy.BatchFlushPolicy
import kotlinx.datetime.Instant
import java.util.UUID

class TelemetryFrameAssembler {
    fun assemble(
        timestamp: Instant,
        location: LocationFix?,
        imu: ImuSample?,
        heading: HeadingSample?,
        deviceState: DeviceStateSnapshot?,
        networkState: NetworkStateSnapshot?,
        motionVector: MotionVector?,
    ): TelemetryFrame = TelemetryFrame(
        timestamp = timestamp,
        location = location,
        imu = imu,
        heading = heading,
        deviceState = deviceState,
        networkState = networkState,
        motionVector = motionVector,
    )
}

open class BatchSequenceStore {
    protected var seq: Int = 0

    open fun next(): Int {
        seq += 1
        return seq
    }

    open fun current(): Int = seq

    open fun restore(value: Int) {
        seq = value.coerceAtLeast(0)
    }

    open fun reset() {
        seq = 0
    }
}

class PersistentBatchSequenceStore(
    context: Context,
) : BatchSequenceStore() {
    private val prefs = context.getSharedPreferences("telemetry_batch_seq", Context.MODE_PRIVATE)

    init {
        restore(prefs.getInt(KEY_SEQ, 0))
    }

    override fun next(): Int {
        val value = super.next()
        prefs.edit().putInt(KEY_SEQ, value).apply()
        return value
    }

    override fun restore(value: Int) {
        super.restore(value)
        prefs.edit().putInt(KEY_SEQ, seq).apply()
    }

    override fun reset() {
        super.reset()
        prefs.edit().putInt(KEY_SEQ, 0).apply()
    }

    private companion object {
        const val KEY_SEQ = "batch_seq_v1"
    }
}

class BatchIdGenerator {
    fun next(): String = UUID.randomUUID().toString()
}

class TelemetryBatchBuilder(
    private val flushPolicy: BatchFlushPolicy,
    private val batchSequenceStore: BatchSequenceStore,
    private val batchIdGenerator: BatchIdGenerator,
) {
    private val frames = mutableListOf<TelemetryFrame>()
    private val events = mutableListOf<DetectedTelemetryEvent>()
    private var windowStartedAt: Instant? = null

    fun addFrame(frame: TelemetryFrame) {
        if (windowStartedAt == null) windowStartedAt = frame.timestamp
        frames += frame
    }

    fun addEvent(event: DetectedTelemetryEvent) {
        events += event
    }

    fun shouldFlush(now: Instant): Boolean {
        if (frames.isEmpty() && events.isEmpty()) return false
        if (frames.size >= flushPolicy.maxFrames) return true
        val started = windowStartedAt ?: return false
        return (now - started).inWholeMilliseconds >= flushPolicy.maxWindowMs
    }

    fun flush(
        deviceId: String,
        driverId: String?,
        sessionId: String,
        trackingMode: TrackingMode?,
        transportMode: String?,
        latestDeviceState: DeviceStateSnapshot?,
        latestNetworkState: NetworkStateSnapshot?,
        headingSummary: HeadingSample?,
        activitySummary: ActivitySample?,
        thresholds: EventThresholdSet?,
        now: Instant,
    ): TelemetryBatch? {
        if (frames.isEmpty() && events.isEmpty()) return null

        val batch = TelemetryBatch(
            deviceId = deviceId,
            driverId = driverId,
            sessionId = sessionId,
            createdAt = now,
            trackingMode = trackingMode,
            transportMode = transportMode,
            batchId = batchIdGenerator.next(),
            batchSeq = batchSequenceStore.next(),
            frames = frames.toList(),
            events = events.toList(),
            deviceState = latestDeviceState,
            networkState = latestNetworkState,
            headingSummary = headingSummary,
            activitySummary = activitySummary,
            tripConfig = thresholds,
        )

        frames.clear()
        events.clear()
        windowStartedAt = null
        return batch
    }
}