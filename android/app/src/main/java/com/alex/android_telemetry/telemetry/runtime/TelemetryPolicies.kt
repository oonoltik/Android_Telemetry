package com.alex.android_telemetry.telemetry.domain.policy

import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet

interface EventThresholdResolver {
    fun getEffectiveThresholds(): EventThresholdSet
}