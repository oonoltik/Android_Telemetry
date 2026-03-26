package com.alex.android_telemetry.telemetry.delivery

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TelemetryDeliveryScheduler(
    private val context: Context,
) {
    fun scheduleImmediate() {
        val request = OneTimeWorkRequestBuilder<TelemetryDeliveryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "telemetry_delivery_immediate",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<TelemetryDeliveryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "telemetry_delivery_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}