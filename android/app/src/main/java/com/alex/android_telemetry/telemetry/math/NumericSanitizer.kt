package com.alex.android_telemetry.telemetry.math

import kotlin.math.pow
import kotlin.math.round

object NumericSanitizer {
    fun metric(x: Double, digits: Int = 6): Double {
        if (!x.isFinite()) return 0.0
        val factor = 10.0.pow(digits)
        return round(x * factor) / factor
    }

    fun metricOptional(x: Double?, digits: Int = 6): Double? = x?.let { metric(it, digits) }

    fun sanitizeDouble(x: Double, digits: Int = 6): Double = metric(x, digits)

    fun sanitizeDouble(x: Double?, digits: Int = 6): Double? = metricOptional(x, digits)
}