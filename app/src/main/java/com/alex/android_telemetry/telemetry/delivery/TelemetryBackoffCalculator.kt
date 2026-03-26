package com.alex.android_telemetry.telemetry.delivery

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class TelemetryBackoffCalculator(
    private val policy: TelemetryDeliveryPolicy,
    private val random: Random = Random.Default,
) {
    fun nextRetryAtEpochMs(nowEpochMs: Long, attempt: Int): Long {
        val exp = policy.baseBackoffMs * 2.0.pow(attempt.toDouble())
        val capped = min(exp.toLong(), policy.maxBackoffMs)
        val jitter = random.nextLong(0, maxOf(1, capped / 4))
        return nowEpochMs + capped + jitter
    }
}