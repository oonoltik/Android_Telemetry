package com.alex.android_telemetry.telemetry.integration

import android.content.Context
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryProcessor
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.domain.model.TelemetryFrame
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.ingest.facade.RoomTelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.ingest.mapper.TelemetryBatchDtoMapper
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase
import com.alex.android_telemetry.telemetry.model.TelemetryBatchDraft
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class RuntimeDeliveryFacade(
    context: Context,
    private val processor: TelemetryDeliveryProcessor,
) : DeliveryFacade {

    private val scheduler = TelemetryDeliveryScheduler(context)

    private val enqueuer by lazy {
        val db = TelemetryDatabase.get(context)
        val repository = TelemetryOutboxRepository(db.telemetryOutboxDao())
        RoomTelemetryBatchEnqueuer(
            mapper = TelemetryBatchDtoMapper(),
            repository = repository,
            scheduler = scheduler,
            json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }

    override suspend fun enqueueOrSend(batch: TelemetryBatchDraft): DeliveryResult {
        enqueuer.enqueue(batch.toDomainBatch())
        scheduler.scheduleImmediate()

        return DeliveryResult(
            accepted = true,
            delivered = false,
            route = null,
            error = null,
        )
    }

    private fun TelemetryBatchDraft.toDomainBatch(): TelemetryBatch {
        return TelemetryBatch(
            deviceId = deviceId,
            driverId = driverId,
            sessionId = sessionId,
            createdAt = Instant.parse(timestamp),
            trackingMode = trackingMode.toDomainTrackingMode(),
            transportMode = transportMode.name.lowercase(),
            batchId = batchId,
            batchSeq = batchSeq,
            frames = samples.map { sample ->
                TelemetryFrame(
                    timestamp = Instant.parse(sample.t),
                    location = if (sample.lat != null && sample.lon != null) {
                        LocationFix(
                            timestamp = Instant.parse(sample.t),
                            lat = sample.lat,
                            lon = sample.lon,
                            horizontalAccuracyM = sample.hAcc,
                            verticalAccuracyM = sample.vAcc,
                            speedMS = sample.speedMps,
                            speedAccuracyMS = sample.speedAcc,
                            bearingDeg = sample.course,
                            bearingAccuracyDeg = sample.courseAcc,
                            provider = "fused",
                        )
                    } else {
                        null
                    },
                )
            },
            events = emptyList(),
            deviceState = null,
            networkState = null,
            headingSummary = null,
            activitySummary = null,
            tripConfig = null,
        )
    }

    private fun com.alex.android_telemetry.telemetry.domain.TrackingMode.toDomainTrackingMode(): TrackingMode {
        return when (this) {
            com.alex.android_telemetry.telemetry.domain.TrackingMode.SINGLE_TRIP -> TrackingMode.SINGLE_TRIP
            com.alex.android_telemetry.telemetry.domain.TrackingMode.DAY_MONITORING -> TrackingMode.DAY_MONITORING
        }
    }
}