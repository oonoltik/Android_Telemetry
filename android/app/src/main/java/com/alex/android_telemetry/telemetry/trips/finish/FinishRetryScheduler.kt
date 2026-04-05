package com.alex.android_telemetry.telemetry.trips.finish

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class FinishRetryScheduler(
    private val context: Context,
) {
    fun scheduleImmediate() {
        Log.d("TelemetryTrip", "scheduleFinishRetryImmediate()")

        val request = OneTimeWorkRequestBuilder<FinishRetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "telemetry_finish_retry_immediate"
    }
}