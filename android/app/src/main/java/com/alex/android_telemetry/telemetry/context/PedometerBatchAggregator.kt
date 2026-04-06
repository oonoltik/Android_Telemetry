package com.alex.android_telemetry.telemetry.context

import com.alex.android_telemetry.telemetry.domain.model.PedometerSample
import com.alex.android_telemetry.telemetry.domain.model.PedometerSummary

class PedometerBatchAggregator {

    fun summarize(samples: List<PedometerSample>): PedometerSummary? {
        if (samples.isEmpty()) return null

        val sorted = samples.sortedBy { it.timestamp }
        val first = sorted.first()
        val last = sorted.last()

        val steps = when {
            first.steps != null && last.steps != null -> (last.steps - first.steps).coerceAtLeast(0)
            else -> sorted.lastOrNull { it.steps != null }?.steps
        }

        val distanceM = when {
            first.distanceM != null && last.distanceM != null -> (last.distanceM - first.distanceM).coerceAtLeast(0.0)
            else -> sorted.lastOrNull { it.distanceM != null }?.distanceM
        }

        val cadenceValues = sorted.mapNotNull { it.cadence }
        val paceValues = sorted.mapNotNull { it.pace }

        return PedometerSummary(
            steps = steps,
            distanceM = distanceM,
            cadence = cadenceValues.averageOrNull(),
            pace = paceValues.averageOrNull(),
        )
    }
}

private fun List<Double>.averageOrNull(): Double? {
    if (isEmpty()) return null
    return average()
}