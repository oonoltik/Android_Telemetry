package com.alex.android_telemetry.telemetry.ingest.repository

import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxDao
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxEntity
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxStatus
import kotlinx.datetime.Clock

class TelemetryOutboxRepository(
    private val dao: TelemetryOutboxDao,
    private val clock: Clock = Clock.System,
) {
    suspend fun enqueue(entity: TelemetryOutboxEntity): Boolean {
        val inserted = dao.insertOrIgnore(entity)
        return inserted != -1L
    }

    suspend fun getNextPending(limit: Int): List<TelemetryOutboxEntity> =
        dao.getNextPending(limit = limit, nowEpochMs = clock.now().toEpochMilliseconds())

    suspend fun markSending(id: Long) {
        dao.markSending(id = id, updatedAtEpochMs = clock.now().toEpochMilliseconds())
    }

    suspend fun markDelivered(id: Long, serverStatus: String?, duplicate: Boolean?) {
        val now = clock.now().toEpochMilliseconds()
        dao.markDelivered(
            id = id,
            deliveredAtEpochMs = now,
            updatedAtEpochMs = now,
            serverStatus = serverStatus,
            serverDuplicate = duplicate,
        )
    }

    suspend fun markRetryWait(id: Long, attemptCount: Int, httpCode: Int?, error: String?, nextRetryAtEpochMs: Long) {
        dao.markRetryWait(
            id = id,
            attemptCount = attemptCount,
            httpCode = httpCode,
            error = error,
            nextRetryAtEpochMs = nextRetryAtEpochMs,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
        )
    }

    suspend fun markContractError(id: Long, httpCode: Int?, error: String?) {
        dao.markTerminalError(
            id = id,
            httpCode = httpCode,
            error = error,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
            status = TelemetryOutboxStatus.CONTRACT_ERROR,
        )
    }

    suspend fun markAuthError(id: Long, httpCode: Int?, error: String?) {
        dao.markTerminalError(
            id = id,
            httpCode = httpCode,
            error = error,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
            status = TelemetryOutboxStatus.AUTH_ERROR,
        )
    }
}
