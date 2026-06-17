package com.alex.android_telemetry.telemetry.crash

import android.content.Context
import android.util.Log
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase
import com.alex.android_telemetry.telemetry.domain.model.DetectedTelemetryEvent
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.domain.model.TelemetryEventType
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.ingest.facade.RoomTelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.ingest.mapper.TelemetryBatchDtoMapper
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class CrashTelemetryEventDispatcher(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val scheduler =
        TelemetryDeliveryScheduler(appContext)

    private val enqueuer by lazy {
        val db =
            TelemetryDatabase.get(appContext)

        RoomTelemetryBatchEnqueuer(
            mapper = TelemetryBatchDtoMapper(),
            repository = TelemetryOutboxRepository(
                db.telemetryOutboxDao(),
            ),
            scheduler = scheduler,
            json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }

    suspend fun enqueueCrashEvent(
        crashId: String,
        event: CrashEvent,
        deviceId: String,
        driverId: String?,
        sessionId: String?,
        videoSessionId: String?,
        cameraType: String,
        preCrashMs: Long,
        postCrashMs: Long,
        telemetrySnapshot: CrashTelemetrySnapshot?,
    ) {
        val safeSessionId =
            sessionId
                ?: telemetrySnapshot?.tripSessionId
                ?: "crash_${event.detectedAtMs}"

        val detectedAt =
            Instant.fromEpochMilliseconds(
                event.detectedAtMs,
            )

        val speedMS =
            telemetrySnapshot
                ?.speedKmh
                ?.div(3.6)

        val frame =
            TelemetryFrame(
                timestamp = detectedAt,
                location = if (
                    telemetrySnapshot?.lat != null &&
                    telemetrySnapshot.lon != null
                ) {
                    LocationFix(
                        timestamp = detectedAt,
                        lat = telemetrySnapshot.lat,
                        lon = telemetrySnapshot.lon,
                        horizontalAccuracyM = telemetrySnapshot.horizontalAccuracyM,
                        speedMS = speedMS,
                        bearingDeg = telemetrySnapshot.headingDeg,
                        provider = "crash_snapshot",
                    )
                } else {
                    null
                },
            )

        val crashTelemetryEvent =
            DetectedTelemetryEvent(
                type = TelemetryEventType.CRASH,
                timestamp = detectedAt,
                intensity = event.gForce,
                speedMS = speedMS,
                eventClass = "emergency",
                subtype = "detected_crash",
                severity = "critical",
                details = "Crash detected by Android client",
                origin = "android_client",
                algoVersion = "android_crash_v1",
                meta = mapOf(
                    "crash_id" to crashId,
                    "source" to event.source,
                    "g_force" to event.gForce.toString(),
                    "video_session_id" to (videoSessionId ?: ""),
                    "camera_type" to cameraType,
                    "pre_crash_ms" to preCrashMs.toString(),
                    "post_crash_ms" to postCrashMs.toString(),
                    "snapshot_at_iso" to (telemetrySnapshot?.capturedAtIso ?: ""),
                    "lat" to (telemetrySnapshot?.lat?.toString() ?: ""),
                    "lon" to (telemetrySnapshot?.lon?.toString() ?: ""),
                    "speed_kmh" to (telemetrySnapshot?.speedKmh?.toString() ?: ""),
                ),
            )

        val batch =
            TelemetryBatch(
                deviceId = deviceId,
                driverId = driverId,
                sessionId = safeSessionId,
                createdAt = detectedAt,
                trackingMode = TrackingMode.SINGLE_TRIP,
                transportMode = "car",
                batchId = "crash_event_$crashId",
                batchSeq = 0,
                frames = listOf(frame),
                events = listOf(crashTelemetryEvent),
            )

        enqueuer.enqueue(batch)
        scheduler.scheduleImmediate()

        Log.d(
            "CrashTelemetry",
            "queued crash telemetry crashId=$crashId sessionId=$safeSessionId driverId=$driverId videoSessionId=$videoSessionId g=${event.gForce}"
        )
        Log.d("CrashTelemetry", "before enqueue")
        enqueuer.enqueue(batch)
        Log.d("CrashTelemetry", "after enqueue")

        Log.d("CrashTelemetry", "before scheduleImmediate")
        scheduler.scheduleImmediate()
        Log.d("CrashTelemetry", "after scheduleImmediate")
    }
}