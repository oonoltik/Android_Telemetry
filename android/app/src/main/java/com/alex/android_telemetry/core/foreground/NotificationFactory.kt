package com.alex.android_telemetry.core.foreground

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeSnapshot

class NotificationFactory(
    private val context: Context
) {
    fun buildIdleNotification(): Notification {
        return baseBuilder()
            .setContentTitle("Telemetry")
            .setContentText("Runtime initialized")
            .build()
    }

    fun buildActiveTripNotification(snapshot: TripRuntimeSnapshot): Notification {
        return baseBuilder()
            .setContentTitle("Trip active")
            .setContentText("session=${snapshot.sessionId.orEmpty()} delivered=${snapshot.batchesDelivered}")
            .setOngoing(true)
            .build()
    }

    fun buildStoppingNotification(snapshot: TripRuntimeSnapshot): Notification {
        return baseBuilder()
            .setContentTitle("Finishing trip")
            .setContentText("session=${snapshot.sessionId.orEmpty()}")
            .setOngoing(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, ForegroundIds.TELEMETRY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }
}
