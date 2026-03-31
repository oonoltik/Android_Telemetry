package com.alex.android_telemetry.telemetry.service

import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode

class TelemetryServiceController(
    private val serviceStarter: TelemetryServiceStarter,
    private val deviceIdProvider: () -> String
) {
    fun startTrip(driverId: String?, trackingMode: TrackingMode, transportMode: TransportMode) {
        serviceStarter.startTrip(deviceIdProvider(), driverId, trackingMode, transportMode)
    }

    fun stopTrip(finishReason: FinishReason) { serviceStarter.stopTrip(finishReason) }
    fun recover() { serviceStarter.recoverTrip() }
}
