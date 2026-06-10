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
        android.util.Log.d(
            "TelemetryWorker",
            "doWork started"
        )
        Log.d(
            "TelemetryDelivery",
            "Worker started workId=$id runAttemptCount=$runAttemptCount"
        )

        return runCatching {
            val graph = TelemetryDeliveryGraph.from(applicationContext)

            var deliveredTotal = 0

            repeat(MAX_RUN_LOOPS) { loopIndex ->
                when (val result = graph.processor.runOnce()) {
                    is DeliveryRunResult.Idle -> {
                        Log.d(
                            "TelemetryDelivery",
                            "Worker idle workId=$id loop=$loopIndex deliveredTotal=$deliveredTotal result=$result"
                        )

                        graph.tripRepository.retryPendingFinishes()

                        android.util.Log.d(
                            "TelemetryWorker",
                            "runOnce finished"
                        )

                        Log.d(
                            "TelemetryDelivery",
                            "Worker success workId=$id deliveredTotal=$deliveredTotal"
                        )

                        return Result.success()
                    }

                    is DeliveryRunResult.Progress -> {
                        deliveredTotal += result.deliveredCount

                        Log.d(
                            "TelemetryDelivery",
                            "Worker progress workId=$id loop=$loopIndex deliveredTotal=$deliveredTotal result=$result"
                        )
                    }
                }
            }

            graph.tripRepository.retryPendingFinishes()

            Log.d(
                "TelemetryDelivery",
                "Worker maxLoopsReached workId=$id deliveredTotal=$deliveredTotal"
            )

            android.util.Log.d(
                "TelemetryWorker",
                "runOnce finished"
            )

            Result.success()
        }.getOrElse {
            Log.e(
                "TelemetryDelivery",
                "Worker failed workId=$id runAttemptCount=$runAttemptCount",
                it
            )
            Result.retry()
        }
    }

    private companion object {
        const val MAX_RUN_LOOPS = 10
    }
}