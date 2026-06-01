package com.alex.android_telemetry.ui.video

import android.content.Context
import android.os.Environment
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

@UnstableApi
class CrashClipExactExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(
    appContext,
    params,
) {
    override suspend fun doWork(): Result {
        val crashId =
            inputData.getString(KEY_CRASH_ID)?.trim().orEmpty()

        if (crashId.isBlank()) {
            android.util.Log.e(
                "CrashClipExactExport",
                "missing crashId"
            )
            return Result.failure()
        }

        val videoRepository =
            DashcamVideoRepository(applicationContext)

        val crashClipRepository =
            CrashClipRepository(
                context = applicationContext,
                videoRepository = videoRepository,
            )

        val crashClip =
            crashClipRepository.findCrashClip(
                crashId,
            ) ?: run {
                android.util.Log.e(
                    "CrashClipExactExport",
                    "crash clip not found crashId=$crashId"
                )
                return Result.success()
            }

        if (crashClip.assemblyState != CrashClipAssemblyState.COMPLETED ||
            crashClip.mergedClipPath == null
        ) {
            android.util.Log.e(
                "CrashClipExactExport",
                "skip crashId=$crashId assemblyState=${crashClip.assemblyState} merged=${crashClip.mergedClipPath}"
            )
            return Result.success()
        }

        crashClipRepository.markExactExportQueuedOrRunning(
            crashId = crashId,
            state = CrashClipExactExportState.EXPORTING,
        )

        val allVideos =
            videoRepository.loadVideos()

        val sourceSegments =
            allVideos
                .filter { segment ->
                    crashClip.segmentPaths.contains(segment.absolutePath)
                }
                .sortedBy { it.startedAtMs }

        if (sourceSegments.isEmpty()) {
            crashClipRepository.markExactExportFailed(
                crashId = crashId,
                error = "No source segments found for exact export",
            )

            android.util.Log.e(
                "CrashClipExactExport",
                "no source segments crashId=$crashId"
            )

            return Result.success()
        }

        val availableStartMs =
            sourceSegments
                .minOfOrNull { it.startedAtMs }
                ?: (crashClip.detectedAtMs - crashClip.preCrashMs)

        val availableEndMs =
            sourceSegments
                .maxOfOrNull { it.endedAtMs }
                ?: (crashClip.detectedAtMs + crashClip.postCrashMs)

        val requestedWindowStartMs =
            crashClip.detectedAtMs - crashClip.preCrashMs

        val requestedWindowEndMs =
            crashClip.detectedAtMs + crashClip.postCrashMs

        val windowStartMs =
            maxOf(
                availableStartMs,
                requestedWindowStartMs,
            )

        val windowEndMs =
            minOf(
                availableEndMs,
                requestedWindowEndMs,
            )

        android.util.Log.d(
            "CrashClipExactExport",
            "window crashId=$crashId detected=${crashClip.detectedAtMs} availableStart=$availableStartMs availableEnd=$availableEndMs requestedStart=$requestedWindowStartMs requestedEnd=$requestedWindowEndMs windowStart=$windowStartMs windowEnd=$windowEndMs pre=${crashClip.preCrashMs} post=${crashClip.postCrashMs}"
        )

        if (windowEndMs <= windowStartMs) {
            crashClipRepository.markExactExportFailed(
                crashId = crashId,
                error = "Invalid exact export window",
            )

            android.util.Log.e(
                "CrashClipExactExport",
                "invalid window crashId=$crashId windowStart=$windowStartMs windowEnd=$windowEndMs"
            )

            return Result.success()
        }

        val outputFile =
            File(
                File(
                    applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "dashcam/exact_crash_packages",
                ),
                "$crashId.mp4",
            )

        val exported =
            CrashClipExactExporter(applicationContext)
                .exportExactWindow(
                    crashId = crashId,
                    segments = sourceSegments,
                    outputFile = outputFile,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs,
                )

        return if (exported) {
            crashClipRepository.markExactExportCompleted(
                crashId = crashId,
                exactClipPath = outputFile.absolutePath,
            )

            Result.success()
        } else {
            crashClipRepository.markExactExportFailed(
                crashId = crashId,
                error = "Media3 exact export failed",
            )

            Result.success()
        }
    }

    companion object {
        const val KEY_CRASH_ID = "crash_id"
    }
}