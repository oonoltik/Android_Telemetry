package com.alex.android_telemetry.ui.video

import android.content.Context
import android.util.Log

class CrashClipUploadRepository(
    context: Context,
) {
    private val api =
        CrashClipUploadApi(context)

    suspend fun upload(
        entity: CrashClipEntity,
        driverId: String,
        deviceId: String,
        cameraType: DashcamCameraType,
    ): Boolean {
        return try {
            api.uploadCrashPackage(
                entity = entity,
                driverId = driverId,
                deviceId = deviceId,
                cameraType = cameraType,
            )
        } catch (error: Throwable) {
            Log.e(
                "CrashClipUpload",
                "upload failed crashId=${entity.crashId}, merged=${entity.mergedClipPath}, driverId=$driverId, deviceId=$deviceId: ${error.message}",
                error,
            )

            false
        }
    }
}