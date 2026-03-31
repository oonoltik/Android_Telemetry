package com.alex.android_telemetry.telemetry.runtime

import android.content.Context
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import kotlinx.datetime.Instant

class PersistentTripRuntimeStateStore(
    context: Context,
) : TripRuntimeStateStore {

    private val prefs = context.getSharedPreferences("telemetry_runtime_state", Context.MODE_PRIVATE)

    override suspend fun save(state: TripRuntimeState) {
        prefs.edit()
            .putString(KEY_SESSION_ID, state.sessionId)
            .putString(KEY_TRACKING_MODE, state.trackingMode?.name)
            .putString(KEY_TELEMETRY_MODE, state.telemetryMode.name)
            .putString(KEY_STARTED_AT, state.startedAt?.toString())
            .putString(KEY_LAST_SAMPLE_AT, state.lastSampleAt?.toString())
            .putString(KEY_LAST_LOCATION_AT, state.lastLocationAt?.toString())
            .putString(KEY_LAST_EVENT_AT, state.lastEventAt?.toString())
            .putFloat(KEY_DISTANCE_M, state.distanceM.toFloat())
            .putBoolean(KEY_IS_FOREGROUND, state.isForegroundCollection)
            .putBoolean(KEY_PENDING_FINISH, state.pendingFinish)
            .apply()
    }

    override suspend fun restore(): TripRuntimeState? {
        val sessionId = prefs.getString(KEY_SESSION_ID, null)
        val telemetryModeName = prefs.getString(KEY_TELEMETRY_MODE, null) ?: return null

        val trackingMode = prefs.getString(KEY_TRACKING_MODE, null)
            ?.let { runCatching { TrackingMode.valueOf(it) }.getOrNull() }

        val telemetryMode = runCatching { TelemetryMode.valueOf(telemetryModeName) }
            .getOrDefault(TelemetryMode.IDLE)

        return TripRuntimeState(
            sessionId = sessionId,
            trackingMode = trackingMode,
            telemetryMode = telemetryMode,
            startedAt = prefs.getString(KEY_STARTED_AT, null)?.let(::parseInstant),
            lastSampleAt = prefs.getString(KEY_LAST_SAMPLE_AT, null)?.let(::parseInstant),
            lastLocationAt = prefs.getString(KEY_LAST_LOCATION_AT, null)?.let(::parseInstant),
            lastEventAt = prefs.getString(KEY_LAST_EVENT_AT, null)?.let(::parseInstant),
            distanceM = prefs.getFloat(KEY_DISTANCE_M, 0f).toDouble(),
            isForegroundCollection = prefs.getBoolean(KEY_IS_FOREGROUND, false),
            pendingFinish = prefs.getBoolean(KEY_PENDING_FINISH, false),
        )
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_TRACKING_MODE = "tracking_mode"
        const val KEY_TELEMETRY_MODE = "telemetry_mode"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_LAST_SAMPLE_AT = "last_sample_at"
        const val KEY_LAST_LOCATION_AT = "last_location_at"
        const val KEY_LAST_EVENT_AT = "last_event_at"
        const val KEY_DISTANCE_M = "distance_m"
        const val KEY_IS_FOREGROUND = "is_foreground"
        const val KEY_PENDING_FINISH = "pending_finish"
    }
}