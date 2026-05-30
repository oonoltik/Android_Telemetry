package com.alex.android_telemetry.core.foreground

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.alex.android_telemetry.R
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeSnapshot

class NotificationFactory(
    private val context: Context,
) {
    fun buildIdleNotification(): Notification {
        return baseBuilder()
            .setContentTitle(context.getString(R.string.notification_telemetry_ready_title))
            .setContentText(context.getString(R.string.notification_telemetry_ready_text))
            .setOngoing(true)
            .build()
    }

    fun buildDayMonitoringNotification(): Notification {
        return baseBuilder()
            .setContentTitle(context.getString(R.string.notification_day_monitoring_title))
            .setContentText(context.getString(R.string.notification_day_monitoring_text))
            .setOngoing(true)
            .build()
    }

    fun buildActiveTripNotification(snapshot: TripRuntimeSnapshot): Notification {
        val modeLabel = when (snapshot.trackingMode) {
            TrackingMode.DAY_MONITORING -> context.getString(R.string.notification_auto_trip_active_title)
            TrackingMode.SINGLE_TRIP -> context.getString(R.string.notification_trip_active_title)
            else -> context.getString(R.string.notification_trip_active_title)
        }

        val session = snapshot.sessionId?.takeLast(8).orEmpty()
        val delivered = snapshot.batchesDelivered

        return baseBuilder()
            .setContentTitle(modeLabel)
            .setContentText(context.getString(R.string.notification_active_trip_text, session, delivered))
            .setOngoing(true)
            .build()
    }

    fun buildStoppingNotification(snapshot: TripRuntimeSnapshot): Notification {
        val session = snapshot.sessionId?.takeLast(8).orEmpty()

        return baseBuilder()
            .setContentTitle(context.getString(R.string.notification_finishing_trip_title))
            .setContentText(context.getString(R.string.notification_finishing_trip_text, session))
            .setOngoing(true)
            .build()
    }

    fun buildReplayingNotification(snapshot: TripRuntimeSnapshot): Notification {
        val session = snapshot.sessionId?.takeLast(8).orEmpty()

        return baseBuilder()
            .setContentTitle(context.getString(R.string.notification_replaying_title))
            .setContentText(context.getString(R.string.notification_replaying_text, session))
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