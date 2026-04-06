package com.alex.android_telemetry.telemetry.daymonitoring

import android.content.Context

data class DayMonitoringState(
    val enabled: Boolean = false,
    val autoStartedTripActive: Boolean = false,
    val autoStartedSessionId: String? = null,
)

class DayMonitoringStateStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DayMonitoringState {
        return DayMonitoringState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            autoStartedTripActive = prefs.getBoolean(KEY_AUTO_STARTED_TRIP_ACTIVE, false),
            autoStartedSessionId = prefs.getString(KEY_AUTO_STARTED_SESSION_ID, null),
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun markAutoTripStarted(sessionId: String?) {
        prefs.edit()
            .putBoolean(KEY_AUTO_STARTED_TRIP_ACTIVE, true)
            .putString(KEY_AUTO_STARTED_SESSION_ID, sessionId)
            .apply()
    }

    fun markAutoTripStopped() {
        prefs.edit()
            .putBoolean(KEY_AUTO_STARTED_TRIP_ACTIVE, false)
            .remove(KEY_AUTO_STARTED_SESSION_ID)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "day_monitoring_state_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_STARTED_TRIP_ACTIVE = "auto_started_trip_active"
        private const val KEY_AUTO_STARTED_SESSION_ID = "auto_started_session_id"
    }
}