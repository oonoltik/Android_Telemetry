package com.alex.android_telemetry.ui.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import android.content.ContentValues
import android.provider.MediaStore
import java.io.FileInputStream

enum class DashcamCameraType {
    ROAD,
    DRIVER,
}

@Serializable
data class DashcamVideoEntity(
    val id: String,
    val absolutePath: String,
    val fileName: String,
    val cameraType: DashcamCameraType,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val isEmergency: Boolean,
    val isProtected: Boolean,
    val tripId: String? = null,
    val sessionId: String? = null,
    val segmentIndex: Int = 0,
    val rollingSessionId: String? = null,
    val gpsStartLat: Double? = null,
    val gpsStartLon: Double? = null,
    val gpsEndLat: Double? = null,
    val gpsEndLon: Double? = null,
    val speedStartKmh: Double? = null,
    val speedEndKmh: Double? = null,
    val telemetrySamplesCount: Int = 0,
    val telemetryEventsCount: Int = 0,
)

data class DashcamStorageStats(
    val totalBytes: Long,
    val protectedBytes: Long,
    val regularBytes: Long,
    val filesCount: Int,
    val protectedFilesCount: Int,
    val maxBytes: Long,
)

class DashcamVideoRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val dashcamDir: File =
        File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "dashcam",
        ).apply {
            mkdirs()
        }

    private val indexFile =
        File(dashcamDir, "dashcam_index.json")

    private val maxStorageBytes: Long =
        2L * 1024L * 1024L * 1024L

    fun getDashcamDirectory(): File {
        dashcamDir.mkdirs()
        return dashcamDir
    }
    fun exportVideoToGallery(
        entity: DashcamVideoEntity,
    ): Boolean {
        return exportFileToGallery(
            sourceFile = File(entity.absolutePath),
            displayName = entity.fileName,
        )
    }

    fun exportFileToGallery(
        sourceFile: File,
        displayName: String,
    ): Boolean {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            return false
        }

        return try {
            val resolver =
                appContext.contentResolver

            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AndroidTelemetry/Dashcam")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

            val uri =
                resolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return false

            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)

            resolver.update(
                uri,
                values,
                null,
                null,
            )

            true
        } catch (_: Exception) {
            false
        }
    }

    fun createSegmentFile(
        cameraType: DashcamCameraType,
        rollingSessionId: String,
        segmentIndex: Int,
    ): File {
        dashcamDir.mkdirs()

        val cameraPrefix =
            when (cameraType) {
                DashcamCameraType.ROAD -> "road"
                DashcamCameraType.DRIVER -> "driver"
            }

        val segmentLabel =
            segmentIndex.toString().padStart(3, '0')

        return File(
            dashcamDir,
            "${cameraPrefix}_${rollingSessionId}_clip_${segmentLabel}.mp4",
        )
    }

    @Synchronized
    fun markEmergencyRollingSession(
        rollingSessionId: String,
    ) {
        val updated =
            loadIndex().map { item ->
                if (item.rollingSessionId == rollingSessionId) {
                    item.copy(
                        isEmergency = true,
                        isProtected = true,
                    )
                } else {
                    item
                }
            }

        saveIndex(updated)
    }

    @Synchronized
    fun protectCrashWindow(
        crashAtMs: Long,
        preCrashMs: Long,
        postCrashMs: Long,
        rollingSessionId: String?,
    ) {
        val windowStartMs =
            crashAtMs - preCrashMs

        val windowEndMs =
            crashAtMs + postCrashMs

        val updated =
            loadIndex().map { item ->
                val overlapsCrashWindow =
                    item.startedAtMs <= windowEndMs &&
                            item.endedAtMs >= windowStartMs

                val sameRollingSession =
                    rollingSessionId != null &&
                            item.rollingSessionId == rollingSessionId

                if (overlapsCrashWindow || sameRollingSession) {
                    item.copy(
                        isEmergency = true,
                        isProtected = true,
                    )
                } else {
                    item
                }
            }

        saveIndex(updated)
    }

    @Synchronized
    fun registerVideo(
        file: File,
        cameraType: DashcamCameraType,
        startedAtMs: Long,
        endedAtMs: Long,
        isEmergency: Boolean = false,
        isProtected: Boolean = false,
        tripId: String? = null,
        sessionId: String? = null,
        rollingSessionId: String? = null,
        segmentIndex: Int = 0,
    ): DashcamVideoEntity {
        val safeEndedAtMs =
            endedAtMs.coerceAtLeast(startedAtMs)

        val durationMs =
            readDuration(file).takeIf { it > 0L }
                ?: (safeEndedAtMs - startedAtMs).coerceAtLeast(0L)

        val entity =
            DashcamVideoEntity(
                id = UUID.randomUUID().toString(),
                absolutePath = file.absolutePath,
                fileName = file.name,
                cameraType = cameraType,
                startedAtMs = startedAtMs,
                endedAtMs = safeEndedAtMs,
                durationMs = durationMs,
                sizeBytes = file.length(),
                isEmergency = isEmergency,
                isProtected = isProtected || isEmergency,
                tripId = tripId,
                sessionId = sessionId,
                rollingSessionId = rollingSessionId,
                segmentIndex = segmentIndex,
            )

        val current =
            loadIndex()
                .filterNot { it.absolutePath == entity.absolutePath }
                .toMutableList()

        current.add(entity)

        saveIndex(
            current.sortedByDescending { it.startedAtMs }
        )

        applyRetentionPolicy()

        return entity
    }

    @Synchronized
    fun loadVideos(): List<DashcamVideoEntity> {
        syncIndex()

        return loadIndex()
            .filter { File(it.absolutePath).exists() }
            .sortedByDescending { it.startedAtMs }
    }

    @Synchronized
    fun loadEmergencyVideos(): List<DashcamVideoEntity> {
        return loadVideos()
            .filter { it.isEmergency || it.isProtected }
    }

    @Synchronized
    fun loadRegularVideos(): List<DashcamVideoEntity> {
        return loadVideos()
            .filterNot { it.isEmergency || it.isProtected }
    }

    @Synchronized
    fun storageStats(): DashcamStorageStats {
        val videos = loadVideos()

        val protectedVideos =
            videos.filter { it.isProtected || it.isEmergency }

        val totalBytes =
            videos.sumOf { it.sizeBytes }

        val protectedBytes =
            protectedVideos.sumOf { it.sizeBytes }

        return DashcamStorageStats(
            totalBytes = totalBytes,
            protectedBytes = protectedBytes,
            regularBytes = totalBytes - protectedBytes,
            filesCount = videos.size,
            protectedFilesCount = protectedVideos.size,
            maxBytes = maxStorageBytes,
        )
    }

    @Synchronized
    fun deleteVideo(
        entity: DashcamVideoEntity,
    ): Boolean {
        val file = File(entity.absolutePath)

        if (entity.isProtected || entity.isEmergency) {
            return false
        }

        if (file.exists()) {
            file.delete()
        }

        saveIndex(
            loadIndex().filterNot {
                it.absolutePath == entity.absolutePath
            }
        )

        return true
    }

    @Synchronized
    fun markEmergency(
        absolutePath: String,
    ) {
        val updated =
            loadIndex().map { item ->
                if (item.absolutePath == absolutePath) {
                    item.copy(
                        isEmergency = true,
                        isProtected = true,
                    )
                } else {
                    item
                }
            }

        saveIndex(updated)
    }

    @Synchronized
    fun applyRetentionPolicy() {
        val videos =
            loadIndex()
                .filter { File(it.absolutePath).exists() }
                .sortedBy { it.startedAtMs }

        var totalBytes =
            videos.sumOf { it.sizeBytes }

        if (totalBytes <= maxStorageBytes) {
            return
        }

        val removable =
            videos.filterNot {
                it.isProtected || it.isEmergency
            }

        val remaining =
            videos.toMutableList()

        for (item in removable) {
            if (totalBytes <= maxStorageBytes) {
                break
            }

            val file = File(item.absolutePath)

            if (file.exists()) {
                file.delete()
            }

            totalBytes -= item.sizeBytes
            remaining.removeAll { it.absolutePath == item.absolutePath }
        }

        saveIndex(
            remaining.sortedByDescending { it.startedAtMs }
        )
    }

    @Synchronized
    private fun syncIndex() {
        val indexed =
            loadIndex()
                .associateBy { it.absolutePath }
                .toMutableMap()

        val files =
            dashcamDir
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile && it.extension.lowercase() == "mp4"
                }

        for (file in files) {
            if (!indexed.containsKey(file.absolutePath)) {
                val timestamp = file.lastModified()

                indexed[file.absolutePath] =
                    DashcamVideoEntity(
                        id = UUID.randomUUID().toString(),
                        absolutePath = file.absolutePath,
                        fileName = file.name,
                        cameraType =
                            if (file.name.contains("driver")) {
                                DashcamCameraType.DRIVER
                            } else {
                                DashcamCameraType.ROAD
                            },
                        startedAtMs = timestamp,
                        endedAtMs = timestamp,
                        durationMs = readDuration(file),
                        sizeBytes = file.length(),
                        isEmergency = file.name.contains("emergency"),
                        isProtected = file.name.contains("emergency"),
                        rollingSessionId = extractRollingSessionId(file.name),
                        segmentIndex = extractSegmentIndex(file.name),
                    )
            }
        }

        saveIndex(
            indexed.values
                .filter { File(it.absolutePath).exists() }
                .sortedByDescending { it.startedAtMs }
        )
    }

    private fun extractRollingSessionId(
        fileName: String,
    ): String? {
        val parts = fileName.removeSuffix(".mp4").split("_")

        return parts.getOrNull(1)
    }

    private fun extractSegmentIndex(
        fileName: String,
    ): Int {
        val marker = "_clip_"

        if (!fileName.contains(marker)) {
            return 0
        }

        return fileName
            .substringAfter(marker)
            .substringBefore(".mp4")
            .toIntOrNull()
            ?: 0
    }

    private fun readDuration(
        file: File,
    ): Long {
        return try {
            val retriever = MediaMetadataRetriever()

            retriever.setDataSource(file.absolutePath)

            val duration =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )

            retriever.release()

            duration?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun loadIndex(): List<DashcamVideoEntity> {
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
    private fun saveIndex(
        items: List<DashcamVideoEntity>,
    ) {
        dashcamDir.mkdirs()

        indexFile.writeText(
            json.encodeToString(items),
        )

        DashcamArchiveRefreshBus.notifyChanged()
    }
}