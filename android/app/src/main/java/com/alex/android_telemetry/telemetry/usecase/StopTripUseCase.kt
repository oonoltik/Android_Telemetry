package com.alex.android_telemetry.telemetry.usecase

import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.service.TelemetryServiceController

class StopTripUseCase(
    private val telemetryServiceController: TelemetryServiceController
) {
    operator fun invoke(finishReason: FinishReason) {
        telemetryServiceController.stopTrip(finishReason)
    }
}
