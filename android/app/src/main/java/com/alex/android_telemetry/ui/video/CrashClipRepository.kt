package com.alex.android_telemetry.ui.video

import android.content.Context
import android.os.Environment
import com.alex.android_telemetry.telemetry.crash.CrashEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import com.alex.android_telemetry.telemetry.crash.CrashTelemetrySnapshot

class CrashClipRepository(
    context: Context,
    private val videoRepository: DashcamVideoRepository,
) {
    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val crashDir =
        File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "dashcam/crash_packages",
        ).apply {
            mkdirs()
        }

    private val indexFile =
        File(crashDir, "crash_clips_index.json")

    private val assembler =
        DashcamClipAssembler()

    @Synchronized
    fun pendingUploads(): List<CrashClipEntity> {
        return loadCrashClips().filter {
            it.uploadState == CrashClipUploadState.QUEUED ||
                    it.uploadState == CrashClipUploadState.FAILED
        }
    }

    @Synchronized
    fun markQueued(
        crashId: String,
    ) {
        updateUploadState(
            crashId = crashId,
            state = CrashClipUploadState.QUEUED,
        )
    }

    @Synchronized
    fun createCrashPackage(
        event: CrashEvent,
        rollingSessionId: String?,
        preCrashMs: Long,
        postCrashMs: Long,
        telemetrySnapshot: CrashTelemetrySnapshot? = null,
        telemetryTimeline: List<CrashTelemetrySnapshot> = emptyList(),
    ): CrashClipEntity {
        val crashId =
            "crash_${event.detectedAtMs}_${UUID.randomUUID().toString().take(8)}"

        val windowStartMs =
            event.detectedAtMs - preCrashMs

        val windowEndMs =
            event.detectedAtMs + postCrashMs

        val segments =
            videoRepository
                .loadVideos()
                .filter { segment ->
                    val overlaps =
                        segment.startedAtMs <= windowEndMs &&
                                segment.endedAtMs >= windowStartMs

                    val sameSession =
                        rollingSessionId != null &&
                                segment.rollingSessionId == rollingSessionId

                    overlaps || sameSession
                }
                .sortedBy { it.startedAtMs }

        val outputFile =
            File(crashDir, "$crashId.mp4")

        val merged =
            assembler.mergeMp4Segments(
                inputFiles = segments.map { File(it.absolutePath) },
                outputFile = outputFile,
            )

        val entity =
            CrashClipEntity(
                crashId = crashId,
                detectedAtMs = event.detectedAtMs,
                gForce = event.gForce,
                rollingSessionId = rollingSessionId,
                preCrashMs = preCrashMs,
                postCrashMs = postCrashMs,
                segmentPaths = segments.map { it.absolutePath },
                mergedClipPath =
                    if (merged) {
                        outputFile.absolutePath
                    } else {
                        null
                    },
                createdAtMs = System.currentTimeMillis(),
                telemetrySnapshot = telemetrySnapshot,
                telemetryTimeline = telemetryTimeline,
            )

        val updated =
            loadCrashClips()
                .filterNot { it.crashId == entity.crashId }
                .plus(entity)
                .sortedByDescending { it.detectedAtMs }

        saveCrashClips(updated)

        return entity
    }

    @Synchronized
    fun loadCrashClips(): List<CrashClipEntity> {
        return try {
            if (!indexFile.exists()) {
                emptyList()
            } else {
                json.decodeFromString(indexFile.readText())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun updateUploadState(
        crashId: String,
        state: CrashClipUploadState,
    ) {
        val updated =
            loadCrashClips().map { item ->
                if (item.crashId == crashId) {
                    item.copy(uploadState = state)
                } else {
                    item
                }
            }

        saveCrashClips(updated)
    }

    @Synchronized
    fun deleteCrashClip(
        crashId: String,
    ): Boolean {
        val current =
            loadCrashClips()

        val target =
            current.firstOrNull {
                it.crashId == crashId
            } ?: return false

        target.mergedClipPath?.let { path ->
            val file = java.io.File(path)

            if (file.exists()) {
                file.delete()
            }
        }

        saveCrashClips(
            current.filterNot {
                it.crashId == crashId
            }
        )

        return true
    }

    private fun saveCrashClips(
        items: List<CrashClipEntity>,
    ) {
        crashDir.mkdirs()

        indexFile.writeText(
            json.encodeToString(items),
        )

        DashcamArchiveRefreshBus.notifyChanged()
    }
}