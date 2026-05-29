package com.alex.android_telemetry.ui.video

import com.alex.android_telemetry.telemetry.crash.CrashTelemetrySnapshot
import kotlinx.serialization.Serializable

@Serializable
data class CrashClipEntity(
    val crashId: String,
    val detectedAtMs: Long,
    val gForce: Double,
    val rollingSessionId: String?,
    val preCrashMs: Long,
    val postCrashMs: Long,
    val segmentPaths: List<String>,
    val mergedClipPath: String?,
    val createdAtMs: Long,
    val telemetrySnapshot: CrashTelemetrySnapshot? = null,
    val telemetryTimeline: List<CrashTelemetrySnapshot> = emptyList(),
    val uploadState: CrashClipUploadState = CrashClipUploadState.LOCAL_ONLY,
)

@Serializable
enum class CrashClipUploadState {
    LOCAL_ONLY,
    QUEUED,
    UPLOADING,
    UPLOADED,
    FAILED,
}