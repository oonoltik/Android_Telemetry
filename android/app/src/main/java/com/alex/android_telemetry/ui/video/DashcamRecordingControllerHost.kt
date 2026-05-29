package com.alex.android_telemetry.ui.video

import android.content.Context

object DashcamRecordingControllerHost {
    @Volatile
    private var controller: DashcamRecordingController? = null

    @Synchronized
    fun get(
        context: Context,
    ): DashcamRecordingController {
        val existing =
            controller

        if (existing != null) {
            return existing
        }

        val appContext =
            context.applicationContext

        val repository =
            DashcamVideoRepository(appContext)

        val created =
            DashcamRecordingController(
                context = appContext,
                repository = repository,
            )

        controller = created

        return created
    }
}