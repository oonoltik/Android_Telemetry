package com.alex.android_telemetry.ui.video

import android.os.Handler
import android.os.Looper
import com.alex.android_telemetry.telemetry.crash.CrashEvent
import com.alex.android_telemetry.telemetry.crash.CrashTelemetryBuffer

class DashcamCrashCoordinator(
    private val recordingController: DashcamRecordingController,
    private val crashClipRepository: CrashClipRepository,
    private val uploadScheduler: CrashClipUploadScheduler,
    private val deviceId: String,
    private val driverIdProvider: () -> String?,
) {
    private val handler =
        Handler(Looper.getMainLooper())

    private val coordinatorCooldownMs: Long = 20_000L
    private val preCrashMs: Long = 10_000L
    private val postCrashMs: Long = 10_000L

    private var lastHandledCrashAtMs: Long = 0L

    fun handleCrashDetected(
        event: CrashEvent,
    ) {
        val now = System.currentTimeMillis()

        if (now - lastHandledCrashAtMs < coordinatorCooldownMs) {
            return
        }

        lastHandledCrashAtMs = now

        val rollingSessionId =
            recordingController.currentRollingSessionId()

        val cameraType =
            recordingController.currentCameraType()

        val telemetrySnapshot =
            CrashTelemetryBuffer.nearestTo(
                event.detectedAtMs,
            )

        val telemetryTimeline =
            CrashTelemetryBuffer.window(
                centerMs = event.detectedAtMs,
                preMs = preCrashMs,
                postMs = postCrashMs,
            )

        recordingController.markCrashDetected(
            event = event,
            preCrashMs = preCrashMs,
            postCrashMs = postCrashMs,
        )

        handler.postDelayed(
            {
                val crashPackage =
                    crashClipRepository.createCrashPackage(
                        event = event,
                        rollingSessionId = rollingSessionId,
                        preCrashMs = preCrashMs,
                        postCrashMs = postCrashMs,
                        telemetrySnapshot = telemetrySnapshot,
                        telemetryTimeline = telemetryTimeline,
                    )

                crashClipRepository.markQueued(
                    crashPackage.crashId,
                )

                val driverId =
                    driverIdProvider()?.trim().orEmpty()

                if (driverId.isBlank()) {
                    crashClipRepository.updateUploadState(
                        crashId = crashPackage.crashId,
                        state = CrashClipUploadState.FAILED,
                    )

                    android.util.Log.e(
                        "CrashClipUpload",
                        "upload skipped: empty driverId crashId=${crashPackage.crashId}"
                    )
                    return@postDelayed
                }

                android.util.Log.d(
                    "CrashClipUpload",
                    "enqueue upload crashId=${crashPackage.crashId} driverId=$driverId deviceId=$deviceId cameraType=$cameraType"
                )

                uploadScheduler.enqueueUpload(
                    crashId = crashPackage.crashId,
                    driverId = driverId,
                    deviceId = deviceId,
                    cameraType = cameraType,
                )
            },
            postCrashMs,
        )
    }
}