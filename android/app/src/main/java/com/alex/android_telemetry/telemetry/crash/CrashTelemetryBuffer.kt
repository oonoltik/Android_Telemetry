package com.alex.android_telemetry.telemetry.crash

import com.alex.android_telemetry.telemetry.model.TelemetrySampleDraft
import java.time.Instant
import kotlin.math.abs

object CrashTelemetryBuffer {
    private const val MAX_AGE_MS: Long = 120_000L
    private const val MAX_ITEMS: Int = 600

    private val samples =
        ArrayDeque<CrashTelemetrySnapshot>()

    @Synchronized
    fun append(
        sample: TelemetrySampleDraft,
        tripSessionId: String?,
    ) {
        val capturedAtMs =
            parseIsoMs(sample.t) ?: System.currentTimeMillis()

        samples.addLast(
            CrashTelemetrySnapshot(
                capturedAtMs = capturedAtMs,
                capturedAtIso = sample.t,
                tripSessionId = tripSessionId,
                lat = sample.lat,
                lon = sample.lon,
                speedKmh = sample.speedMps?.times(3.6),
                headingDeg = sample.course,
                horizontalAccuracyM = sample.hAcc,
            )
        )

        trimLocked(System.currentTimeMillis())
    }

    @Synchronized
    fun nearestTo(
        timestampMs: Long,
    ): CrashTelemetrySnapshot? {
        trimLocked(System.currentTimeMillis())

        return samples.minByOrNull {
            abs(it.capturedAtMs - timestampMs)
        }
    }

    @Synchronized
    fun window(
        centerMs: Long,
        preMs: Long,
        postMs: Long,
    ): List<CrashTelemetrySnapshot> {
        trimLocked(System.currentTimeMillis())

        val startMs =
            centerMs - preMs

        val endMs =
            centerMs + postMs

        return samples
            .filter {
                it.capturedAtMs in startMs..endMs
            }
            .sortedBy {
                it.capturedAtMs
            }
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    private fun trimLocked(
        nowMs: Long,
    ) {
        while (
            samples.isNotEmpty() &&
            nowMs - samples.first().capturedAtMs > MAX_AGE_MS
        ) {
            samples.removeFirst()
        }

        while (samples.size > MAX_ITEMS) {
            samples.removeFirst()
        }
    }

    private fun parseIsoMs(
        value: String,
    ): Long? {
        return runCatching {
            Instant.parse(value).toEpochMilli()
        }.getOrNull()
    }
}