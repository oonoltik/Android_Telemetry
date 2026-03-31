package com.alex.android_telemetry.telemetry.usecase

import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode
import com.alex.android_telemetry.telemetry.service.TelemetryServiceController

class StartTripUseCase(
    private val telemetryServiceController: TelemetryServiceController
) {
    operator fun invoke(driverId: String?, trackingMode: TrackingMode, transportMode: TransportMode) {
        telemetryServiceController.startTrip(driverId, trackingMode, transportMode)
    }
}
