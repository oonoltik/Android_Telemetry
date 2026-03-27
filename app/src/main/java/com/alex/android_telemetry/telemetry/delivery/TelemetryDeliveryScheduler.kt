package com.alex.android_telemetry.telemetry.delivery

import android.content.Context
import android.util.Log
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
        Log.d("TelemetryDelivery", "scheduleImmediate()")

        val request = OneTimeWorkRequestBuilder<TelemetryDeliveryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleImmediateDebug() {
        Log.d("TelemetryDelivery", "scheduleImmediateDebug()")

        val request = OneTimeWorkRequestBuilder<TelemetryDeliveryWorker>()
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePeriodic() {
        Log.d("TelemetryDelivery", "schedulePeriodic()")

        val request = PeriodicWorkRequestBuilder<TelemetryDeliveryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val IMMEDIATE_WORK_NAME = "telemetry_delivery_immediate"
        private const val PERIODIC_WORK_NAME = "telemetry_delivery_periodic"
    }
}