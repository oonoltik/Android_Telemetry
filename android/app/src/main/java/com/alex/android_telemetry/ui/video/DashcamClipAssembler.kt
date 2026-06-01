package com.alex.android_telemetry.ui.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

class DashcamClipAssembler {
    fun mergeMp4Segments(
        inputFiles: List<File>,
        outputFile: File,
    ): Boolean {
        val sources =
            inputFiles
                .filter { it.exists() && it.length() > 0L }

        if (sources.isEmpty()) {
            return false
        }

        outputFile.parentFile?.mkdirs()

        return try {
            val firstExtractor = MediaExtractor()
            firstExtractor.setDataSource(sources.first().absolutePath)

            val trackMappings = mutableListOf<TrackMapping>()

            for (i in 0 until firstExtractor.trackCount) {
                val format = firstExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()

                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMappings.add(
                        TrackMapping(
                            sourceTrackIndex = i,
                            format = format,
                            mime = mime,
                        )
                    )
                }
            }

            firstExtractor.release()

            if (trackMappings.isEmpty()) {
                return false
            }

            val muxer =
                MediaMuxer(
                    outputFile.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                )

            val rotationDegrees =
                readRotationDegrees(sources.first())

            if (rotationDegrees != 0) {
                muxer.setOrientationHint(rotationDegrees)
            }

            val outputTrackMap =
                trackMappings.associate { mapping ->
                    mapping.mime to muxer.addTrack(mapping.format)
                }

            muxer.start()

            val timeOffsetsUs =
                outputTrackMap.keys.associateWith { 0L }.toMutableMap()

            val buffer =
                ByteBuffer.allocate(2 * 1024 * 1024)

            val info =
                android.media.MediaCodec.BufferInfo()

            for (source in sources) {
                val durationUs =
                    readDurationMs(source) * 1000L

                val extractor = MediaExtractor()
                extractor.setDataSource(source.absolutePath)

                for (trackIndex in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(trackIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    val outputTrackIndex = outputTrackMap[mime] ?: continue
                    val offsetUs = timeOffsetsUs[mime] ?: 0L

                    extractor.selectTrack(trackIndex)

                    while (true) {
                        buffer.clear()

                        val sampleSize =
                            extractor.readSampleData(buffer, 0)

                        if (sampleSize < 0) {
                            break
                        }

                        info.set(
                            0,
                            sampleSize,
                            offsetUs + extractor.sampleTime.coerceAtLeast(0L),
                            normalizeSampleFlags(extractor.sampleFlags),
                        )

                        muxer.writeSampleData(
                            outputTrackIndex,
                            buffer,
                            info,
                        )

                        extractor.advance()
                    }

                    extractor.unselectTrack(trackIndex)
                }

                extractor.release()

                for (mime in timeOffsetsUs.keys.toList()) {
                    timeOffsetsUs[mime] =
                        timeOffsetsUs.getValue(mime) + durationUs
                }
            }

            muxer.stop()
            muxer.release()

            outputFile.exists() && outputFile.length() > 0L
        } catch (_: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }

            false
        }
    }

    private fun readDurationMs(
        file: File,
    ): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val duration =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull() ?: 0L

            retriever.release()

            duration
        } catch (_: Exception) {
            0L
        }
    }

    private fun readRotationDegrees(
        file: File,
    ): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val rotation =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
                )?.toIntOrNull() ?: 0

            retriever.release()

            when (rotation) {
                0, 90, 180, 270 -> rotation
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun normalizeSampleFlags(
        flags: Int,
    ): Int {
        var normalizedFlags = 0

        if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            normalizedFlags = normalizedFlags or android.media.MediaCodec.BUFFER_FLAG_SYNC_FRAME
        }

        if ((flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            normalizedFlags = normalizedFlags or android.media.MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }

        return normalizedFlags
    }

    private data class TrackMapping(
        val sourceTrackIndex: Int,
        val format: MediaFormat,
        val mime: String,
    )
}