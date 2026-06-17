package com.alex.android_telemetry.telemetry.dashcam

import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import kotlin.math.abs

data class DashcamTelemetrySessionSnapshot(
    val recordingStartLat: Double?,
    val recordingStartLon: Double?,
    val recordingEndLat: Double?,
    val recordingEndLon: Double?,
    val sessionStartSampleT: String?,
    val sessionEndSampleT: String?,
    val totalSamples: Int,
    val totalEvents: Int,
    val sessionStartSpeedKmh: Double?,
    val sessionEndSpeedKmh: Double?,
    val sessionEventTypes: List<String>,
)

object DashcamTelemetrySessionSnapshotStore {
    private const val MAX_FRAMES = 20_000
    private const val MAX_EVENTS = 2_000

    private val frames =
        ArrayDeque<TelemetryFrame>()

    private val events =
        ArrayDeque<DetectedTelemetryEvent>()

    @Synchronized
    fun recordFrame(
        frame: TelemetryFrame,
    ) {
        frames.addLast(frame)

        while (frames.size > MAX_FRAMES) {
            frames.removeFirst()
        }
    }

    @Synchronized
    fun recordEvent(
        event: DetectedTelemetryEvent,
    ) {
        events.addLast(event)

        while (events.size > MAX_EVENTS) {
            events.removeFirst()
        }
    }

    @Synchronized
    fun snapshot(
        startedAtMs: Long,
        endedAtMs: Long,
    ): DashcamTelemetrySessionSnapshot {
        val sessionFrames =
            frames
                .filter { frame ->
                    val timestampMs =
                        frame.timestamp.toEpochMilliseconds()

                    timestampMs in startedAtMs..endedAtMs
                }
                .sortedBy { frame ->
                    frame.timestamp.toEpochMilliseconds()
                }

        val sessionEvents =
            events
                .filter { event ->
                    val timestampMs =
                        event.timestamp.toEpochMilliseconds()

                    timestampMs in startedAtMs..endedAtMs
                }
                .sortedBy { event ->
                    event.timestamp.toEpochMilliseconds()
                }

        val firstFrame =
            sessionFrames.firstOrNull()

        val lastFrame =
            sessionFrames.lastOrNull()

        val firstLocationFrame =
            sessionFrames.firstOrNull { frame ->
                frame.location != null
            }

        val lastLocationFrame =
            sessionFrames.lastOrNull { frame ->
                frame.location != null
            }

        val startSpeedKmh =
            firstFrame
                ?.location
                ?.speedMS
                ?.takeIf { speed ->
                    speed >= 0.0
                }
                ?.times(3.6)

        val endSpeedKmh =
            lastFrame
                ?.location
                ?.speedMS
                ?.takeIf { speed ->
                    speed >= 0.0
                }
                ?.times(3.6)

        val eventTypes =
            sessionEvents
                .map { event ->
                    event.type.name.lowercase()
                }
                .distinct()
                .sorted()

        return DashcamTelemetrySessionSnapshot(
            recordingStartLat = firstLocationFrame?.location?.lat,
            recordingStartLon = firstLocationFrame?.location?.lon,
            recordingEndLat = lastLocationFrame?.location?.lat,
            recordingEndLon = lastLocationFrame?.location?.lon,
            sessionStartSampleT = firstFrame?.timestamp?.toString(),
            sessionEndSampleT = lastFrame?.timestamp?.toString(),
            totalSamples = sessionFrames.size,
            totalEvents = sessionEvents.size,
            sessionStartSpeedKmh = startSpeedKmh,
            sessionEndSpeedKmh = endSpeedKmh,
            sessionEventTypes = eventTypes,
        )
    }

    @Synchronized
    fun clear() {
        frames.clear()
        events.clear()
    }
}