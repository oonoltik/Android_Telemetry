package com.alex.android_telemetry.telemetry.context

import com.alex.android_telemetry.telemetry.domain.model.AltimeterSample
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSummary

class AltimeterBatchAggregator {

    fun summarize(samples: List<AltimeterSample>): AltimeterSummary? {
        if (samples.isEmpty()) return null

        val relAltValues = samples.mapNotNull { it.relativeAltitudeM }
        val pressureValues = samples.mapNotNull { it.pressureKpa }

        if (relAltValues.isEmpty() && pressureValues.isEmpty()) {
            return null
        }

        return AltimeterSummary(
            relAltMMin = relAltValues.minOrNull(),
            relAltMMax = relAltValues.maxOrNull(),
            pressureKpaMin = pressureValues.minOrNull(),
            pressureKpaMax = pressureValues.maxOrNull(),
        )
    }
}