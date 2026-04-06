package com.alex.android_telemetry.telemetry.context

import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionContextSummary
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionSample
import kotlinx.datetime.Instant

class ScreenInteractionContextAggregator {

    fun summarize(
        samples: List<ScreenInteractionSample>,
        windowStartedAt: Instant,
        windowEndedAt: Instant,
        recentThresholdSec: Double = 15.0,
    ): ScreenInteractionContextSummary? {
        if (samples.isEmpty()) {
            return ScreenInteractionContextSummary(
                count = 0,
                recent = false,
                activeSec = 0.0,
                lastAt = null,
                windowStartedAt = windowStartedAt,
                windowEndedAt = windowEndedAt,
            )
        }

        val sorted = samples.sortedBy { it.timestamp }
        val lastAt = sorted.last().timestamp
        val recent = (windowEndedAt.toEpochMilliseconds() - lastAt.toEpochMilliseconds()) <= (recentThresholdSec * 1000.0).toLong()

        var activeMs = 0L
        for (sample in sorted) {
            val segStart = sample.activeStartedAt?.toEpochMilliseconds() ?: sample.timestamp.toEpochMilliseconds()
            val segEnd = sample.activeEndedAt?.toEpochMilliseconds() ?: sample.timestamp.toEpochMilliseconds()
            activeMs += (segEnd - segStart).coerceAtLeast(0L)
        }

        return ScreenInteractionContextSummary(
            count = sorted.size,
            recent = recent,
            activeSec = activeMs.toDouble() / 1000.0,
            lastAt = lastAt,
            windowStartedAt = windowStartedAt,
            windowEndedAt = windowEndedAt,
        )
    }
}