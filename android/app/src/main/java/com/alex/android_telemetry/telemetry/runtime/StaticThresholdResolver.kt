package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.model.EventThresholdSet
import com.alex.android_telemetry.telemetry.domain.policy.EventThresholdResolver

class StaticThresholdResolver : EventThresholdResolver {
    override fun getEffectiveThresholds(): EventThresholdSet {
        return EventThresholdSet(
            accelSharpG = 0.18,
            accelEmergencyG = 0.28,
            brakeSharpG = 0.22,
            brakeEmergencyG = 0.32,
            turnSharpG = 0.22,
            turnEmergencyG = 0.30,
            roadLowG = 0.45,
            roadHighG = 0.75,
            roadWindowS = 0.40,
            // Combined risk — iOS V2Thresholds defaults
            combinedLatMinG = 0.35,
            accelInTurnSharpG = 0.22,
            accelInTurnEmergencyG = 0.32,
            brakeInTurnSharpG = 0.22,
            brakeInTurnEmergencyG = 0.32,
            combinedCooldownS = 0.8,
        )
    }
}