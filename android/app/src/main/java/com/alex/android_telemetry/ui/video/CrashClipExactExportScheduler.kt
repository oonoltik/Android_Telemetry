package com.alex.android_telemetry.ui.video

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class CrashClipExactExportScheduler(
    private val context: Context,
) {
    fun enqueueExactExport(
        crashId: String,
        driverId: String,
        deviceId: String,
        cameraType: DashcamCameraType,
    ) {
        val data =
            Data.Builder()
                .putString(
                    CrashClipExactExportWorker.KEY_CRASH_ID,
                    crashId,
                )
                .putString(
                    CrashClipExactExportWorker.KEY_DRIVER_ID,
                    driverId,
                )
                .putString(
                    CrashClipExactExportWorker.KEY_DEVICE_ID,
                    deviceId,
                )
                .putString(
                    CrashClipExactExportWorker.KEY_CAMERA_TYPE,
                    cameraType.name,
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<CrashClipExactExportWorker>()
                .setInputData(data)
                .addTag(TAG)
                .addTag("$TAG:$crashId")
                .build()

        WorkManager
            .getInstance(context)
            .enqueue(request)

        android.util.Log.d(
            "CrashClipExactExport",
            "enqueue exact export crashId=$crashId"
        )
    }

    companion object {
        private const val TAG =
            "crash_clip_exact_export"
    }
}