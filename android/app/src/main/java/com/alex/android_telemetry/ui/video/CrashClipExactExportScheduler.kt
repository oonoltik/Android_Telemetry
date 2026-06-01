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
    ) {
        val data =
            Data.Builder()
                .putString(
                    CrashClipExactExportWorker.KEY_CRASH_ID,
                    crashId,
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
        private const val TAG = "crash_clip_exact_export"
    }
}