package com.alex.android_telemetry.ui.video

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class CrashClipUploadScheduler(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val workManager =
        WorkManager.getInstance(appContext)

    fun enqueueUpload(
        crashId: String,
        driverId: String,
        deviceId: String,
        cameraType: DashcamCameraType,
    ) {
        val data =
            Data.Builder()
                .putString(
                    CrashClipUploadWorker.KEY_CRASH_ID,
                    crashId,
                )
                .putString(
                    CrashClipUploadWorker.KEY_DRIVER_ID,
                    driverId,
                )
                .putString(
                    CrashClipUploadWorker.KEY_DEVICE_ID,
                    deviceId,
                )
                .putString(
                    CrashClipUploadWorker.KEY_CAMERA_TYPE,
                    cameraType.name,
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<CrashClipUploadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS,
                )
                .build()

        workManager.enqueueUniqueWork(
            "crash_upload_$crashId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}