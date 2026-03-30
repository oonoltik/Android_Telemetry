package com.alex.android_telemetry.telemetry.ingest.repository

import android.util.Log
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxDao
import com.alex.android_telemetry.telemetry.ingest.storage.TelemetryOutboxEntity
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
        dao.getNextPending(
            limit = limit,
            nowEpochMs = clock.now().toEpochMilliseconds(),
        )

    suspend fun markSending(id: Long) {
        dao.markInFlightById(
            id = id,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
        )
    }

    suspend fun markDelivered(
        id: Long,
        serverStatus: String?,
        duplicate: Boolean?,
    ) {
        val now = clock.now().toEpochMilliseconds()
        dao.markDelivered(
            id = id,
            deliveredAtEpochMs = now,
            updatedAtEpochMs = now,
            serverStatus = serverStatus,
            serverDuplicate = duplicate,
        )
    }

    suspend fun markRetryWait(
        id: Long,
        attemptCount: Int,
        httpCode: Int?,
        error: String?,
        nextRetryAtEpochMs: Long,
    ) {
        dao.markRetryWait(
            id = id,
            attemptCount = attemptCount,
            httpCode = httpCode,
            error = error,
            nextRetryAtEpochMs = nextRetryAtEpochMs,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
        )
    }

    suspend fun reclaimStaleInFlight(staleBeforeEpochMs: Long) {
        val now = clock.now().toEpochMilliseconds()
        dao.reclaimStaleInFlight(
            staleBeforeEpochMs = staleBeforeEpochMs,
            updatedAtEpochMs = now,
        )
    }

    suspend fun claimNextForDelivery(limit: Int): List<TelemetryOutboxEntity> {
        val now = clock.now().toEpochMilliseconds()

        val candidates = dao.findCandidatesForDelivery(
            nowEpochMs = now,
            limit = limit,
        )

        if (candidates.isEmpty()) return emptyList()

        val ids = candidates.map { it.id }

        val updated = dao.markInFlight(
            ids = ids,
            updatedAtEpochMs = now,
        )
        Log.d("TelemetryDelivery", "claimNextForDelivery(): candidates=${candidates.size}")
        Log.d("TelemetryDelivery", "claimNextForDelivery(): updated=$updated ids=$ids")

        if (updated == 0) return emptyList()

        return candidates
    }

    suspend fun markTerminalFailed(
        id: Long,
        httpCode: Int?,
        error: String?,
    ) {
        dao.markTerminalFailed(
            id = id,
            httpCode = httpCode,
            error = error,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
        )
    }

    suspend fun markAuthFailed(
        id: Long,
        httpCode: Int?,
        error: String?,
    ) {
        dao.markAuthFailed(
            id = id,
            httpCode = httpCode,
            error = error,
            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
        )
    }

    suspend fun countReadyForDelivery(nowEpochMs: Long): Int =
        dao.countReadyForDelivery(nowEpochMs)

    suspend fun countUndeliveredForSession(sessionId: String): Int =
        dao.countUndeliveredForSession(sessionId)
}