package com.alex.android_telemetry.core.foreground

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeSnapshot

class NotificationFactory(
    private val context: Context,
) {
    fun buildIdleNotification(): Notification {
        return baseBuilder()
            .setContentTitle("Telemetry ready")
            .setContentText("Background monitoring service is ready")
            .setOngoing(true)
            .build()
    }

    fun buildDayMonitoringNotification(): Notification {
        return baseBuilder()
            .setContentTitle("Day monitoring active")
            .setContentText("Waiting for driving activity")
            .setOngoing(true)
            .build()
    }

    fun buildActiveTripNotification(snapshot: TripRuntimeSnapshot): Notification {
        val modeLabel = when (snapshot.trackingMode) {
            TrackingMode.DAY_MONITORING -> "Auto trip active"
            TrackingMode.SINGLE_TRIP -> "Trip active"
            else -> "Trip active"
        }

        val session = snapshot.sessionId?.takeLast(8).orEmpty()
        val delivered = snapshot.batchesDelivered

        return baseBuilder()
            .setContentTitle(modeLabel)
            .setContentText("Session $session · delivered batches $delivered")
            .setOngoing(true)
            .build()
    }

    fun buildStoppingNotification(snapshot: TripRuntimeSnapshot): Notification {
        val session = snapshot.sessionId?.takeLast(8).orEmpty()

        return baseBuilder()
            .setContentTitle("Finishing trip")
            .setContentText("Uploading remaining telemetry · Session $session")
            .setOngoing(true)
            .build()
    }

    fun buildReplayingNotification(snapshot: TripRuntimeSnapshot): Notification {
        val session = snapshot.sessionId?.takeLast(8).orEmpty()

        return baseBuilder()
            .setContentTitle("Telemetry replaying")
            .setContentText("Restoring pending trip data · Session $session")
            .setOngoing(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, ForegroundIds.TELEMETRY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }
}