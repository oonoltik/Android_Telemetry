package com.alex.android_telemetry.telemetry.trips.finish

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryGraph

class FinishRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            Log.d("TelemetryTrip", "FinishRetryWorker started")
            val graph = TelemetryDeliveryGraph.from(applicationContext)
            graph.tripRepository.retryPendingFinishes()
            Result.success()
        }.getOrElse {
            Log.e("TelemetryTrip", "FinishRetryWorker failed", it)
            Result.retry()
        }
    }
}