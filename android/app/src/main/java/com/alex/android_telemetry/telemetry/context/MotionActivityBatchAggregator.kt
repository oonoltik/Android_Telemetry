package com.alex.android_telemetry.telemetry.context

import com.alex.android_telemetry.telemetry.domain.model.ActivityContextSummary
import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.domain.model.MotionActivitySummary
import kotlinx.datetime.Instant
import kotlin.math.max

class MotionActivityBatchAggregator {

    fun summarize(
        samples: List<ActivitySample>,
        windowStartedAt: Instant,
        windowEndedAt: Instant,
    ): MotionActivityAggregationResult? {
        if (samples.isEmpty()) return null

        val sorted = samples.sortedBy { it.timestamp }
        val bucketKeys = listOf(
            "stationary",
            "walking",
            "running",
            "cycling",
            "automotive",
            "unknown",
        )

        val durations = linkedMapOf<String, Double>()
        bucketKeys.forEach { durations[it] = 0.0 }

        val startMs = windowStartedAt.toEpochMilliseconds()
        val endMs = windowEndedAt.toEpochMilliseconds()
        if (endMs <= startMs) return null

        for (i in sorted.indices) {
            val current = sorted[i]
            val segStart = max(current.timestamp.toEpochMilliseconds(), startMs)
            val segEnd = when {
                i < sorted.lastIndex -> minOf(sorted[i + 1].timestamp.toEpochMilliseconds(), endMs)
                else -> endMs
            }
            val durationSec = ((segEnd - segStart).coerceAtLeast(0L)).toDouble() / 1000.0
            val key = normalizeActivity(current.dominant)
            durations[key] = (durations[key] ?: 0.0) + durationSec
        }

        val total = durations.values.sum().takeIf { it > 0.0 } ?: return null
        val dominant = durations.maxByOrNull { it.value }?.key ?: "unknown"
        val bestConfidence = sorted.maxByOrNull { confidenceRank(it.confidence) }?.confidence ?: "low"
        val lastSample = sorted.last()
        val isAutomotiveNow = normalizeActivity(lastSample.dominant) == "automotive"

        val nonAutomotiveStreakSec = computeNonAutomotiveStreakSec(
            sorted = sorted,
            windowStartedAt = windowStartedAt,
            windowEndedAt = windowEndedAt,
        )

        return MotionActivityAggregationResult(
            motionActivity = MotionActivitySummary(
                dominant = dominant,
                confidence = lastSample.confidence ?: bestConfidence,
                durationsSec = durations.toMap(),
            ),
            activityContext = ActivityContextSummary(
                dominant = dominant,
                bestConfidence = bestConfidence,
                stationaryShare = durations["stationary"]?.div(total),
                walkingShare = durations["walking"]?.div(total),
                runningShare = durations["running"]?.div(total),
                cyclingShare = durations["cycling"]?.div(total),
                automotiveShare = durations["automotive"]?.div(total),
                unknownShare = durations["unknown"]?.div(total),
                nonAutomotiveStreakSec = nonAutomotiveStreakSec,
                isAutomotiveNow = isAutomotiveNow,
                windowStartedAt = windowStartedAt,
                windowEndedAt = windowEndedAt,
            ),
        )
    }

    private fun computeNonAutomotiveStreakSec(
        sorted: List<ActivitySample>,
        windowStartedAt: Instant,
        windowEndedAt: Instant,
    ): Double {
        val endMs = windowEndedAt.toEpochMilliseconds()
        val startMs = windowStartedAt.toEpochMilliseconds()

        var cursor = endMs
        for (i in sorted.indices.reversed()) {
            val sample = sorted[i]
            val key = normalizeActivity(sample.dominant)
            if (key == "automotive") {
                return ((endMs - max(sample.timestamp.toEpochMilliseconds(), startMs)).coerceAtLeast(0L)).toDouble() / 1000.0
            }
            cursor = minOf(cursor, sample.timestamp.toEpochMilliseconds())
        }

        return ((endMs - startMs).coerceAtLeast(0L)).toDouble() / 1000.0
    }

    private fun normalizeActivity(raw: String?): String {
        return when (raw?.lowercase()) {
            "stationary" -> "stationary"
            "walking" -> "walking"
            "running" -> "running"
            "cycling" -> "cycling"
            "automotive" -> "automotive"
            else -> "unknown"
        }
    }

    private fun confidenceRank(raw: String?): Int {
        return when (raw?.lowercase()) {
            "high" -> 3
            "medium" -> 2
            "low" -> 1
            else -> 0
        }
    }
}

data class MotionActivityAggregationResult(
    val motionActivity: MotionActivitySummary,
    val activityContext: ActivityContextSummary,
)