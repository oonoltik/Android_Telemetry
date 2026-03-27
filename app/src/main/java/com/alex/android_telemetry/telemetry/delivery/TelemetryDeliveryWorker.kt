package com.alex.android_telemetry.telemetry.delivery

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TelemetryDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d("TelemetryDelivery", "Worker started")

        val graph = TelemetryDeliveryGraph.from(applicationContext)

        repeat(10) {
            when (val result = graph.processor.runOnce()) {
                is DeliveryRunResult.Idle -> {
                    Log.d("TelemetryDelivery", "Result: $result")
                    return Result.success()
                }
                is DeliveryRunResult.Progress -> {
                    Log.d("TelemetryDelivery", "Result: $result")
                }
            }
        }

        return Result.success()
    }
}