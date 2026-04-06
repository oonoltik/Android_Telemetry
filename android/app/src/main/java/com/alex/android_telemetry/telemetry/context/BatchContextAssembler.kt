package com.alex.android_telemetry.telemetry.context

import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSample
import com.alex.android_telemetry.telemetry.domain.model.PedometerSample
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionSample
import kotlinx.datetime.Instant

class BatchContextAssembler(
    private val motionActivityBatchAggregator: MotionActivityBatchAggregator = MotionActivityBatchAggregator(),
    private val pedometerBatchAggregator: PedometerBatchAggregator = PedometerBatchAggregator(),
    private val altimeterBatchAggregator: AltimeterBatchAggregator = AltimeterBatchAggregator(),
    private val screenInteractionContextAggregator: ScreenInteractionContextAggregator = ScreenInteractionContextAggregator(),
) {

    fun assemble(
        activitySamples: List<ActivitySample>,
        pedometerSamples: List<PedometerSample>,
        altimeterSamples: List<AltimeterSample>,
        screenInteractionSamples: List<ScreenInteractionSample>,
        windowStartedAt: Instant,
        windowEndedAt: Instant,
    ): BatchContextAggregate {
        val motion = motionActivityBatchAggregator.summarize(
            samples = activitySamples,
            windowStartedAt = windowStartedAt,
            windowEndedAt = windowEndedAt,
        )

        return BatchContextAggregate(
            motionActivity = motion?.motionActivity,
            activityContext = motion?.activityContext,
            pedometer = pedometerBatchAggregator.summarize(pedometerSamples),
            altimeter = altimeterBatchAggregator.summarize(altimeterSamples),
            screenInteractionContext = screenInteractionContextAggregator.summarize(
                samples = screenInteractionSamples,
                windowStartedAt = windowStartedAt,
                windowEndedAt = windowEndedAt,
            ),
        )
    }
}