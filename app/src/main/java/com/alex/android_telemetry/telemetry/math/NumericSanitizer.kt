package com.alex.android_telemetry.sensors.normalizer

import kotlin.math.pow

object NumericSanitizer {
    fun raw(x: Double): Double = if (x.isFinite()) x else 0.0

    fun rawOptional(x: Double?): Double? = x?.takeIf { it.isFinite() }

    fun metric(x: Double, digits: Int = 6): Double {
        if (!x.isFinite()) return 0.0
        val scale = 10.0.pow(digits.toDouble())
        return kotlin.math.round(x * scale) / scale
    }

    fun metricOptional(x: Double?, digits: Int = 6): Double? = x?.let { metric(it, digits) }
}
