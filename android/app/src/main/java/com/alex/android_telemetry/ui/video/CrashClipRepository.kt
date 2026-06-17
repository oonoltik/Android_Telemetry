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
            it.assemblyState == CrashClipAssemblyState.COMPLETED &&
                    it.mergedClipPath != null &&
                    (
                            it.uploadState == CrashClipUploadState.QUEUED ||
                                    it.uploadState == CrashClipUploadState.FAILED
                            )
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
    fun hasCompletedPostCrashSegment(
        event: CrashEvent,
        rollingSessionId: String?,
        postCrashMs: Long,
    ): Boolean {
        val windowEndMs =
            event.detectedAtMs + postCrashMs

        return videoRepository
            .loadVideos()
            .any { segment ->
                val file =
                    File(segment.absolutePath)

                val sameSession =
                    rollingSessionId == null ||
                            segment.rollingSessionId == rollingSessionId

                sameSession &&
                        file.exists() &&
                        file.length() > 0L &&
                        segment.startedAtMs <= windowEndMs &&
                        segment.endedAtMs >= windowEndMs
            }
    }

    @Synchronized
    fun pendingAssemblyRetries(
        maxAttempts: Int = 5,
    ): List<CrashClipEntity> {
        return loadCrashClips()
            .filter { item ->
                item.assemblyState == CrashClipAssemblyState.FAILED &&
                        item.assemblyAttempts < maxAttempts
            }
            .sortedBy { item ->
                item.lastAssemblyAttemptAtMs ?: item.createdAtMs
            }
    }

    @Synchronized
    fun createCrashPackage(
        crashId: String,
        event: CrashEvent,
        rollingSessionId: String?,
        preCrashMs: Long,
        postCrashMs: Long,
        telemetrySnapshot: CrashTelemetrySnapshot?,
        telemetryTimeline: List<CrashTelemetrySnapshot>,
    ): CrashClipEntity {


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
                        rollingSessionId == null ||
                                segment.rollingSessionId == rollingSessionId

                    overlaps && sameSession
                }
                .sortedBy { it.startedAtMs }

        val safeSegments =
            if (segments.isNotEmpty()) {
                segments
            } else {
                videoRepository
                    .loadVideos()
                    .filter { segment ->
                        segment.startedAtMs <= windowEndMs &&
                                segment.endedAtMs >= windowStartMs
                    }
                    .sortedBy { it.startedAtMs }
            }

        android.util.Log.d(
            "CrashClipPackage",
            "create crashId=$crashId windowStart=$windowStartMs windowEnd=$windowEndMs rollingSessionId=$rollingSessionId segments=${safeSegments.size}"
        )

        safeSegments.forEach { segment ->
            android.util.Log.d(
                "CrashClipPackage",
                "segment file=${segment.fileName} start=${segment.startedAtMs} end=${segment.endedAtMs} duration=${segment.durationMs} emergency=${segment.isEmergency} protected=${segment.isProtected} exists=${File(segment.absolutePath).exists()} size=${File(segment.absolutePath).length()}"
            )
        }

        val outputFile =
            File(crashDir, "$crashId.mp4")

        val merged =
            safeSegments.isNotEmpty() &&
                    assembler.mergeMp4Segments(
                        inputFiles = safeSegments.map { File(it.absolutePath) },
                        outputFile = outputFile,
                    )

        val assemblyError =
            when {
                safeSegments.isEmpty() ->
                    "No segments found for crash window"

                !merged ->
                    "MP4 merge failed"

                else ->
                    null
            }

        val entity =
            CrashClipEntity(
                crashId = crashId,
                detectedAtMs = event.detectedAtMs,
                gForce = event.gForce,
                rollingSessionId = rollingSessionId,
                preCrashMs = preCrashMs,
                postCrashMs = postCrashMs,
                segmentPaths = safeSegments.map { it.absolutePath },
                mergedClipPath =
                    if (merged) {
                        outputFile.absolutePath
                    } else {
                        null
                    },
                createdAtMs = System.currentTimeMillis(),
                telemetrySnapshot = telemetrySnapshot,
                telemetryTimeline = telemetryTimeline,
                assemblyState =
                    if (merged) {
                        CrashClipAssemblyState.COMPLETED
                    } else {
                        CrashClipAssemblyState.FAILED
                    },
                assemblyAttempts = 1,
                lastAssemblyAttemptAtMs = System.currentTimeMillis(),
                lastAssemblyError = assemblyError,
            )

        val updated =
            loadCrashClips()
                .filterNot { it.crashId == entity.crashId }
                .plus(entity)
                .sortedByDescending { it.detectedAtMs }

        saveCrashClips(updated)

        android.util.Log.d(
            "CrashClipSaved",
            "saved crashId=${entity.crashId} clips=${updated.size}"
        )

        return entity
    }

    @Synchronized
    fun retryAssembly(
        crashId: String,
    ): CrashClipEntity? {
        val current =
            loadCrashClips()

        val existing =
            current.firstOrNull { item ->
                item.crashId == crashId
            } ?: return null

        val windowStartMs =
            existing.detectedAtMs - existing.preCrashMs

        val windowEndMs =
            existing.detectedAtMs + existing.postCrashMs

        val segments =
            videoRepository
                .loadVideos()
                .filter { segment ->
                    val overlaps =
                        segment.startedAtMs <= windowEndMs &&
                                segment.endedAtMs >= windowStartMs

                    val sameSession =
                        existing.rollingSessionId == null ||
                                segment.rollingSessionId == existing.rollingSessionId

                    val file =
                        File(segment.absolutePath)

                    overlaps &&
                            sameSession &&
                            file.exists() &&
                            file.length() > 0L
                }
                .sortedBy { it.startedAtMs }

        val safeSegments =
            if (segments.isNotEmpty()) {
                segments
            } else {
                videoRepository
                    .loadVideos()
                    .filter { segment ->
                        val file =
                            File(segment.absolutePath)

                        segment.startedAtMs <= windowEndMs &&
                                segment.endedAtMs >= windowStartMs &&
                                file.exists() &&
                                file.length() > 0L
                    }
                    .sortedBy { it.startedAtMs }
            }

        val outputFile =
            File(crashDir, "${existing.crashId}.mp4")

        if (outputFile.exists()) {
            outputFile.delete()
        }

        val merged =
            safeSegments.isNotEmpty() &&
                    assembler.mergeMp4Segments(
                        inputFiles = safeSegments.map { File(it.absolutePath) },
                        outputFile = outputFile,
                    )

        val assemblyError =
            when {
                safeSegments.isEmpty() ->
                    "No segments found for crash window"

                !merged ->
                    "MP4 merge failed"

                else ->
                    null
            }

        val updatedEntity =
            existing.copy(
                segmentPaths = safeSegments.map { it.absolutePath },
                mergedClipPath =
                    if (merged) {
                        outputFile.absolutePath
                    } else {
                        null
                    },
                assemblyState =
                    if (merged) {
                        CrashClipAssemblyState.COMPLETED
                    } else {
                        CrashClipAssemblyState.FAILED
                    },
                assemblyAttempts = existing.assemblyAttempts + 1,
                lastAssemblyAttemptAtMs = System.currentTimeMillis(),
                lastAssemblyError = assemblyError,
                uploadState =
                    if (merged && existing.uploadState == CrashClipUploadState.FAILED) {
                        CrashClipUploadState.LOCAL_ONLY
                    } else {
                        existing.uploadState
                    },
            )

        saveCrashClips(
            current
                .filterNot { item -> item.crashId == crashId }
                .plus(updatedEntity)
                .sortedByDescending { item -> item.detectedAtMs }
        )

        android.util.Log.d(
            "CrashClipPackage",
            "retry assembly crashId=$crashId merged=$merged attempts=${updatedEntity.assemblyAttempts} error=$assemblyError"
        )

        return updatedEntity
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

    @Synchronized
    fun findCrashClip(
        crashId: String,
    ): CrashClipEntity? {
        return loadCrashClips()
            .firstOrNull { item ->
                item.crashId == crashId
            }
    }

    @Synchronized
    fun markExactExportQueuedOrRunning(
        crashId: String,
        state: CrashClipExactExportState,
    ) {
        val current =
            loadCrashClips()

        val updated =
            current.map { item ->
                if (item.crashId == crashId) {
                    item.copy(
                        exactExportState = state,
                        exactExportAttempts = item.exactExportAttempts + 1,
                        lastExactExportAttemptAtMs = System.currentTimeMillis(),
                        lastExactExportError = null,
                    )
                } else {
                    item
                }
            }

        saveCrashClips(updated)

        android.util.Log.d(
            "CrashClipExactExport",
            "state crashId=$crashId state=$state"
        )
    }

    @Synchronized
    fun markExactExportCompleted(
        crashId: String,
        exactClipPath: String,
    ) {
        val current =
            loadCrashClips()

        val updated =
            current.map { item ->
                if (item.crashId == crashId) {
                    item.copy(
                        exactExportState = CrashClipExactExportState.COMPLETED,
                        exactClipPath = exactClipPath,
                        mergedClipPath = exactClipPath,
                        lastExactExportAttemptAtMs = System.currentTimeMillis(),
                        lastExactExportError = null,
                    )
                } else {
                    item
                }
            }

        saveCrashClips(updated)

        DashcamArchiveRefreshBus.notifyChanged()

        android.util.Log.d(
            "CrashClipExactExport",
            "completed crashId=$crashId exactClipPath=$exactClipPath"
        )
    }

    @Synchronized
    fun markExactExportFailed(
        crashId: String,
        error: String,
    ) {
        val current =
            loadCrashClips()

        val updated =
            current.map { item ->
                if (item.crashId == crashId) {
                    item.copy(
                        exactExportState = CrashClipExactExportState.FAILED,
                        lastExactExportAttemptAtMs = System.currentTimeMillis(),
                        lastExactExportError = error,
                    )
                } else {
                    item
                }
            }

        saveCrashClips(updated)

        DashcamArchiveRefreshBus.notifyChanged()

        android.util.Log.e(
            "CrashClipExactExport",
            "failed crashId=$crashId error=$error"
        )
    }
}