package com.alex.android_telemetry.telemetry.service

object TelemetryServiceActions {
    const val ACTION_START_TRIP: String = "com.alex.android_telemetry.action.START_TRIP"
    const val ACTION_STOP_TRIP: String = "com.alex.android_telemetry.action.STOP_TRIP"
    const val ACTION_RECOVER_TRIP: String = "com.alex.android_telemetry.action.RECOVER_TRIP"

    const val EXTRA_DEVICE_ID: String = "extra_device_id"
    const val EXTRA_DRIVER_ID: String = "extra_driver_id"
    const val EXTRA_TRACKING_MODE: String = "extra_tracking_mode"
    const val EXTRA_TRANSPORT_MODE: String = "extra_transport_mode"
    const val EXTRA_FINISH_REASON: String = "extra_finish_reason"
}
