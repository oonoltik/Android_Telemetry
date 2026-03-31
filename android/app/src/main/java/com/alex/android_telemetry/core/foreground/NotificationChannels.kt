package com.alex.android_telemetry.core.foreground

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            ForegroundIds.TELEMETRY_CHANNEL_ID,
            "Telemetry trip tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground telemetry trip tracking"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
