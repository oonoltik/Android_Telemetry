package com.alex.android_telemetry.ui.video

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class CrashClipExactExporter(
    private val context: Context,
) {
    fun exportExactWindow(
        crashId: String,
        segments: List<DashcamVideoEntity>,
        outputFile: File,
        windowStartMs: Long,
        windowEndMs: Long,
    ): Boolean {
        val safeSegments =
            segments
                .filter { segment ->
                    val file =
                        File(segment.absolutePath)

                    file.exists() &&
                            file.length() > 0L &&
                            segment.startedAtMs < windowEndMs &&
                            segment.endedAtMs > windowStartMs
                }
                .sortedBy { it.startedAtMs }

        if (safeSegments.isEmpty()) {
            android.util.Log.e(
                "CrashClipExactExport",
                "no segments crashId=$crashId"
            )
            return false
        }

        outputFile.parentFile?.mkdirs()

        if (outputFile.exists()) {
            outputFile.delete()
        }

        val editedItems =
            safeSegments.mapNotNull { segment ->
                val sourceFile =
                    File(segment.absolutePath)

                val effectiveStartMs =
                    max(windowStartMs, segment.startedAtMs)

                val effectiveEndMs =
                    min(windowEndMs, segment.endedAtMs)

                if (effectiveEndMs <= effectiveStartMs) {
                    return@mapNotNull null
                }

                val sourceStartMs =
                    (effectiveStartMs - segment.startedAtMs)
                        .coerceAtLeast(0L)

                val sourceEndMs =
                    (effectiveEndMs - segment.startedAtMs)
                        .coerceAtLeast(sourceStartMs + 1L)

                android.util.Log.d(
                    "CrashClipExactExport",
                    "clip crashId=$crashId file=${segment.fileName} sourceStartMs=$sourceStartMs sourceEndMs=$sourceEndMs segmentStart=${segment.startedAtMs} segmentEnd=${segment.endedAtMs}"
                )

                val mediaItem =
                    MediaItem.Builder()
                        .setUri(sourceFile.toURI().toString())
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(sourceStartMs)
                                .setEndPositionMs(sourceEndMs)
                                .build()
                        )
                        .build()

                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(false)
                    .setRemoveVideo(false)
                    .build()
            }

        if (editedItems.isEmpty()) {
            android.util.Log.e(
                "CrashClipExactExport",
                "empty editedItems crashId=$crashId"
            )
            return false
        }

        val completed =
            AtomicBoolean(false)

        val latch =
            CountDownLatch(1)

        val transformerRef =
            AtomicReference<Transformer?>()

        return try {
            val sequence =
                EditedMediaItemSequence(editedItems)

            val composition =
                Composition.Builder(sequence)
                    .build()

            val mainHandler =
                Handler(Looper.getMainLooper())

            mainHandler.post {
                try {
                    val transformer =
                        Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                    ) {
                                        android.util.Log.d(
                                            "CrashClipExactExport",
                                            "completed crashId=$crashId file=${outputFile.absolutePath} size=${outputFile.length()}"
                                        )
                                        completed.set(true)
                                        latch.countDown()
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException,
                                    ) {
                                        android.util.Log.e(
                                            "CrashClipExactExport",
                                            "failed crashId=$crashId error=${exportException.message}",
                                            exportException,
                                        )
                                        completed.set(false)
                                        latch.countDown()
                                    }
                                }
                            )
                            .build()

                    transformerRef.set(transformer)

                    transformer.start(
                        composition,
                        outputFile.absolutePath,
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "CrashClipExactExport",
                        "start exception crashId=$crashId error=${e.message}",
                        e,
                    )
                    completed.set(false)
                    latch.countDown()
                }
            }

            val finished =
                latch.await(
                    90L,
                    TimeUnit.SECONDS,
                )

            if (!finished) {
                mainHandler.post {
                    transformerRef.get()?.cancel()
                }

                android.util.Log.e(
                    "CrashClipExactExport",
                    "timeout crashId=$crashId"
                )
            }

            val ok =
                finished &&
                        completed.get() &&
                        outputFile.exists() &&
                        outputFile.length() > 0L

            if (!ok && outputFile.exists()) {
                outputFile.delete()
            }

            ok
        } catch (e: Exception) {
            android.util.Log.e(
                "CrashClipExactExport",
                "exception crashId=$crashId error=${e.message}",
                e,
            )

            if (outputFile.exists()) {
                outputFile.delete()
            }

            false
        }
    }
}