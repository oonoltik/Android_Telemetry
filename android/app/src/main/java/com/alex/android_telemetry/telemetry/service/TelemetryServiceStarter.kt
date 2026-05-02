package com.alex.android_telemetry.telemetry.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode

class TelemetryServiceStarter(
    private val context: Context
) {
    fun startTrip(
        deviceId: String,
        driverId: String?,
        trackingMode: TrackingMode,
        transportMode: TransportMode
    ) {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_START_TRIP
            putExtra(TelemetryServiceActions.EXTRA_DEVICE_ID, deviceId)
            putExtra(TelemetryServiceActions.EXTRA_DRIVER_ID, driverId)
            putExtra(TelemetryServiceActions.EXTRA_TRACKING_MODE, trackingMode.name)
            putExtra(TelemetryServiceActions.EXTRA_TRANSPORT_MODE, transportMode.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTrip(finishReason: FinishReason) {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_STOP_TRIP
            putExtra(TelemetryServiceActions.EXTRA_FINISH_REASON, finishReason.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun recoverTrip() {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_RECOVER_TRIP
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun enableDayMonitoring() {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_ENABLE_DAY_MONITORING
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun disableDayMonitoring() {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_DISABLE_DAY_MONITORING
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun autoStartTrip(
        deviceId: String,
        driverId: String?,
        transportMode: TransportMode = TransportMode.UNKNOWN
    ) {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_AUTO_START_TRIP
            putExtra(TelemetryServiceActions.EXTRA_DEVICE_ID, deviceId)
            putExtra(TelemetryServiceActions.EXTRA_DRIVER_ID, driverId)
            putExtra(TelemetryServiceActions.EXTRA_TRACKING_MODE, TrackingMode.DAY_MONITORING.name)
            putExtra(TelemetryServiceActions.EXTRA_TRANSPORT_MODE, transportMode.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun autoStopTrip(finishReason: FinishReason = FinishReason.UNKNOWN) {
        val intent = Intent(context, TelemetryForegroundService::class.java).apply {
            action = TelemetryServiceActions.ACTION_AUTO_STOP_TRIP
            putExtra(TelemetryServiceActions.EXTRA_FINISH_REASON, finishReason.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}