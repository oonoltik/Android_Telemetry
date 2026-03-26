package com.alex.android_telemetry.telemetry.ingest.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telemetry_outbox",
    indices = [
        Index(value = ["batch_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["next_retry_at_epoch_ms"]),
    ],
)
data class TelemetryOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "batch_seq")
    val batchSeq: Int,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_retry_at_epoch_ms")
    val nextRetryAtEpochMs: Long? = null,
    @ColumnInfo(name = "last_http_code")
    val lastHttpCode: Int? = null,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "server_status")
    val serverStatus: String? = null,
    @ColumnInfo(name = "server_duplicate")
    val serverDuplicate: Boolean? = null,
    @ColumnInfo(name = "delivered_at_epoch_ms")
    val deliveredAtEpochMs: Long? = null,
)

object TelemetryOutboxStatus {
    const val PENDING = "pending"
    const val IN_FLIGHT = "in_flight"
    const val RETRY_WAIT = "retry_wait"
    const val SENT = "sent"
    const val FAILED_TERMINAL = "failed_terminal"
    const val FAILED_AUTH = "failed_auth"
}
