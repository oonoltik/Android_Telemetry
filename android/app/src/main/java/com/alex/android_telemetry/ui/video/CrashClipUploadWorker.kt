package com.alex.android_telemetry.ui.video

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CrashClipUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val crashId =
            inputData.getString(KEY_CRASH_ID)
                ?: return Result.failure()

        val driverId =
            inputData.getString(KEY_DRIVER_ID)
                ?: return Result.retry()

        val deviceId =
            inputData.getString(KEY_DEVICE_ID)
                ?: return Result.retry()

        val cameraTypeRaw =
            inputData.getString(KEY_CAMERA_TYPE)
                ?: DashcamCameraType.ROAD.name

        val repository =
            DashcamVideoRepository(applicationContext)

        val crashRepository =
            CrashClipRepository(
                context = applicationContext,
                videoRepository = repository,
            )

        val uploadRepository =
            CrashClipUploadRepository(applicationContext)

        val crashClip =
            crashRepository
                .loadCrashClips()
                .firstOrNull { it.crashId == crashId }
                ?: return Result.failure()

        android.util.Log.d(
            "CrashClipUpload",
            "worker started crashId=$crashId driverId=$driverId deviceId=$deviceId cameraType=$cameraTypeRaw"
        )

        crashRepository.updateUploadState(
            crashId = crashId,
            state = CrashClipUploadState.UPLOADING,
        )

        val uploaded =
            uploadRepository.upload(
                entity = crashClip,
                driverId = driverId,
                deviceId = deviceId,
                cameraType =
                    runCatching {
                        DashcamCameraType.valueOf(cameraTypeRaw)
                    }.getOrDefault(DashcamCameraType.ROAD),
            )

        crashRepository.updateUploadState(
            crashId = crashId,
            state =
                if (uploaded) {
                    CrashClipUploadState.UPLOADED
                } else {
                    CrashClipUploadState.FAILED
                },
        )

        android.util.Log.d(
            "CrashClipUpload",
            "worker finished crashId=$crashId uploaded=$uploaded"
        )

        return if (uploaded) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_CRASH_ID = "crash_id"
        const val KEY_DRIVER_ID = "driver_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_CAMERA_TYPE = "camera_type"
    }
}