package com.alex.android_telemetry.telemetry.ingest.facade

import android.util.Log
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.domain.model.TelemetryBatch
import com.alex.android_telemetry.telemetry.ingest.TelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.ingest.mapper.TelemetryBatchDtoMapper
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxEntity
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxStatus
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class RoomTelemetryBatchEnqueuer(
    private val mapper: TelemetryBatchDtoMapper,
    private val repository: TelemetryOutboxRepository,
    private val scheduler: TelemetryDeliveryScheduler,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    },
    private val clock: Clock = Clock.System,
) : TelemetryBatchEnqueuer {

    override suspend fun enqueue(batch: TelemetryBatch) {
        val dto = mapper.map(batch)
        val payload = json.encodeToString(dto)
        val now = clock.now().toEpochMilliseconds()

        val inserted = repository.enqueue(
            TelemetryOutboxEntity(
                batchId = batch.batchId,
                sessionId = batch.sessionId,
                batchSeq = batch.batchSeq,
                status = TelemetryOutboxStatus.PENDING,
                payloadJson = payload,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )

        Log.d(
            "TelemetryDelivery",
            "enqueue(): sessionId=${batch.sessionId} batchId=${batch.batchId} seq=${batch.batchSeq} inserted=$inserted"
        )

        if (inserted) {
            scheduler.scheduleImmediate()
        }
    }
}