package com.alex.android_telemetry.telemetry.domain.policy

import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet

interface EventThresholdResolver {
    fun getEffectiveThresholds(): EventThresholdSet
}

data class BatchFlushPolicy(
    val maxWindowMs: Long = 10_000,
    val maxFrames: Int = 50,
)
