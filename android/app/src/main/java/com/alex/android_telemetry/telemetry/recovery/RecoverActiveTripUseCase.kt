package com.alex.android_telemetry.telemetry.recovery

import com.alex.android_telemetry.telemetry.service.TelemetryServiceController

class RecoverActiveTripUseCase(
    private val telemetryServiceController: TelemetryServiceController
) {
    operator fun invoke() {
        telemetryServiceController.recover()
    }
}
