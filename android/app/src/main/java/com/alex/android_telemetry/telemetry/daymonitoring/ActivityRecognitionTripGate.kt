package com.alex.android_telemetry.telemetry.daymonitoring

import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import kotlinx.datetime.Instant

class ActivityRecognitionTripGate(
    private val automotiveStartThresholdSec: Long = 10L,
    private val nonAutomotiveStopThresholdSec: Long = 80L,
) {
    private var automotiveStreakStartedAt: Instant? = null
    private var nonAutomotiveStreakStartedAt: Instant? = null
    private var lastObservedActivity: String? = null

    fun reset() {
        automotiveStreakStartedAt = null
        nonAutomotiveStreakStartedAt = null
        lastObservedActivity = null
    }

    fun evaluate(
        sample: ActivitySample,
        tripIsActive: Boolean,
        tripWasAutoStarted: Boolean,
        manualTripActive: Boolean,
    ): DayMonitoringDecision {
        val normalized = normalizeActivity(sample.dominant)
        val confidence = normalizeConfidence(sample.confidence)

        lastObservedActivity = normalized

        if (manualTripActive) {
            automotiveStreakStartedAt = null
            nonAutomotiveStreakStartedAt = null
            return DayMonitoringDecision.None
        }

        val isAutomotive = normalized == "automotive"
        val automotiveEligible = isAutomotive && confidence != "low"

        if (!tripIsActive) {
            nonAutomotiveStreakStartedAt = null

            if (automotiveEligible) {
                if (automotiveStreakStartedAt == null) {
                    automotiveStreakStartedAt = sample.timestamp
                    return DayMonitoringDecision.None
                }

                val elapsedSec = secondsBetween(automotiveStreakStartedAt!!, sample.timestamp)
                if (elapsedSec >= automotiveStartThresholdSec) {
                    automotiveStreakStartedAt = null
                    return DayMonitoringDecision.AutoStart
                }
            } else {
                automotiveStreakStartedAt = null
            }

            return DayMonitoringDecision.None
        }

        if (!tripWasAutoStarted) {
            automotiveStreakStartedAt = null
            nonAutomotiveStreakStartedAt = null
            return DayMonitoringDecision.None
        }

        if (isAutomotive) {
            nonAutomotiveStreakStartedAt = null
            return DayMonitoringDecision.None
        }

        automotiveStreakStartedAt = null

        if (nonAutomotiveStreakStartedAt == null) {
            nonAutomotiveStreakStartedAt = sample.timestamp
            return DayMonitoringDecision.None
        }

        val elapsedSec = secondsBetween(nonAutomotiveStreakStartedAt!!, sample.timestamp)
        if (elapsedSec >= nonAutomotiveStopThresholdSec) {
            nonAutomotiveStreakStartedAt = null
            return DayMonitoringDecision.AutoStop
        }

        return DayMonitoringDecision.None
    }

    private fun secondsBetween(start: Instant, end: Instant): Long {
        val deltaMs = end.toEpochMilliseconds() - start.toEpochMilliseconds()
        return (deltaMs.coerceAtLeast(0L) / 1000L)
    }

    private fun normalizeActivity(raw: String?): String {
        return when (raw?.lowercase()) {
            "automotive" -> "automotive"
            "walking" -> "walking"
            "running" -> "running"
            "cycling" -> "cycling"
            "stationary" -> "stationary"
            else -> "unknown"
        }
    }

    private fun normalizeConfidence(raw: String?): String {
        return when (raw?.lowercase()) {
            "high" -> "high"
            "medium" -> "medium"
            "low" -> "low"
            else -> "low"
        }
    }
}

sealed interface DayMonitoringDecision {
    data object None : DayMonitoringDecision
    data object AutoStart : DayMonitoringDecision
    data object AutoStop : DayMonitoringDecision
}