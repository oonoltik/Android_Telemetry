package com.alex.android_telemetry.telemetry.ingest.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(entity: TelemetryOutboxEntity): Long

    @Query("SELECT * FROM telemetry_outbox WHERE batch_id = :batchId LIMIT 1")
    suspend fun getByBatchId(batchId: String): TelemetryOutboxEntity?

    @Query(
        """
        SELECT * FROM telemetry_outbox
        WHERE status IN (:pendingStatus, :retryWaitStatus)
          AND (next_retry_at_epoch_ms IS NULL OR next_retry_at_epoch_ms <= :nowEpochMs)
        ORDER BY created_at_epoch_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun getNextPending(
        limit: Int,
        nowEpochMs: Long,
        pendingStatus: String = TelemetryOutboxStatus.PENDING,
        retryWaitStatus: String = TelemetryOutboxStatus.RETRY_WAIT,
    ): List<TelemetryOutboxEntity>

    @Query(
        """
        UPDATE telemetry_outbox
        SET status = :status,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun markSending(
        id: Long,
        status: String = TelemetryOutboxStatus.SENDING,
        updatedAtEpochMs: Long,
    )

    @Query(
        """
        UPDATE telemetry_outbox
        SET status = :status,
            updated_at_epoch_ms = :updatedAtEpochMs,
            delivered_at_epoch_ms = :deliveredAtEpochMs,
            server_status = :serverStatus,
            server_duplicate = :serverDuplicate
        WHERE id = :id
        """,
    )
    suspend fun markDelivered(
        id: Long,
        deliveredAtEpochMs: Long,
        updatedAtEpochMs: Long,
        serverStatus: String?,
        serverDuplicate: Boolean?,
        status: String = TelemetryOutboxStatus.DELIVERED,
    )

    @Query(
        """
        UPDATE telemetry_outbox
        SET status = :status,
            attempt_count = :attemptCount,
            last_http_code = :httpCode,
            last_error = :error,
            next_retry_at_epoch_ms = :nextRetryAtEpochMs,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun markRetryWait(
        id: Long,
        attemptCount: Int,
        httpCode: Int?,
        error: String?,
        nextRetryAtEpochMs: Long,
        updatedAtEpochMs: Long,
        status: String = TelemetryOutboxStatus.RETRY_WAIT,
    )

    @Query(
        """
        UPDATE telemetry_outbox
        SET status = :status,
            last_http_code = :httpCode,
            last_error = :error,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun markTerminalError(
        id: Long,
        httpCode: Int?,
        error: String?,
        updatedAtEpochMs: Long,
        status: String,
    )
}
