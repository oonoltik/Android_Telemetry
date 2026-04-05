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

interface LegacyBatchSequenceStore {
    fun next(): Int
    fun current(): Int
    fun restore(value: Int)
    fun reset()
}

class PersistentLegacyBatchSequenceStore(
    context: Context,
) : LegacyBatchSequenceStore {
    private val prefs = context.getSharedPreferences("telemetry_batch_seq", Context.MODE_PRIVATE)
    private val lock = Any()
    private var seq: Int = 0

    init {
        restore(prefs.getInt(KEY_SEQ, 0))
    }

    override fun next(): Int = synchronized(lock) {
        seq += 1
        prefs.edit().putInt(KEY_SEQ, seq).commit()
        seq
    }

    override fun current(): Int = synchronized(lock) {
        seq
    }

    override fun restore(value: Int) {
        synchronized(lock) {
            seq = value.coerceAtLeast(0)
            prefs.edit().putInt(KEY_SEQ, seq).commit()
        }
    }

    override fun reset() {
        synchronized(lock) {
            seq = 0
            prefs.edit().putInt(KEY_SEQ, 0).commit()
        }
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
    private val batchSequenceStore: LegacyBatchSequenceStore,
    private val batchIdGenerator: BatchIdGenerator,
) {
    private val lock = Any()
    private val frames = mutableListOf<TelemetryFrame>()
    private val events = mutableListOf<DetectedTelemetryEvent>()
    private var windowStartedAt: Instant? = null

    fun addFrame(frame: TelemetryFrame) {
        synchronized(lock) {
            if (windowStartedAt == null) {
                windowStartedAt = frame.timestamp
            }
            frames += frame
        }
    }

    fun addEvent(event: DetectedTelemetryEvent) {
        synchronized(lock) {
            events += event
        }
    }

    fun shouldFlush(now: Instant): Boolean = synchronized(lock) {
        if (frames.isEmpty() && events.isEmpty()) return@synchronized false
        if (frames.size >= flushPolicy.maxFrames) return@synchronized true
        val started = windowStartedAt ?: return@synchronized false
        (now - started).inWholeMilliseconds >= flushPolicy.maxWindowMs
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
    ): TelemetryBatch? = synchronized(lock) {
        if (frames.isEmpty() && events.isEmpty()) return@synchronized null

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
        batch
    }
}