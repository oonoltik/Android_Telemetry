package com.alex.android_telemetry.telemetry.context

import com.alex.android_telemetry.telemetry.domain.model.ActivityContextSummary
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSummary
import com.alex.android_telemetry.telemetry.domain.model.MotionActivitySummary
import com.alex.android_telemetry.telemetry.domain.model.PedometerSummary
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionContextSummary

data class BatchContextAggregate(
    val motionActivity: MotionActivitySummary? = null,
    val activityContext: ActivityContextSummary? = null,
    val pedometer: PedometerSummary? = null,
    val altimeter: AltimeterSummary? = null,
    val screenInteractionContext: ScreenInteractionContextSummary? = null,
)