package com.alex.android_telemetry.core.recovery

import android.content.Context

class RecoveryUxStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("recovery_ux", Context.MODE_PRIVATE)

    fun markTelemetryRestored() {
        prefs.edit()
            .putBoolean(KEY_TELEMETRY_RESTORED, true)
            .apply()
    }

    fun markReplayResumed() {
        prefs.edit()
            .putBoolean(KEY_REPLAY_RESUMED, true)
            .apply()
    }

    fun consumeTelemetryRestored(): Boolean {
        val value = prefs.getBoolean(KEY_TELEMETRY_RESTORED, false)
        if (value) {
            prefs.edit().putBoolean(KEY_TELEMETRY_RESTORED, false).apply()
        }
        return value
    }

    fun consumeReplayResumed(): Boolean {
        val value = prefs.getBoolean(KEY_REPLAY_RESUMED, false)
        if (value) {
            prefs.edit().putBoolean(KEY_REPLAY_RESUMED, false).apply()
        }
        return value
    }

    companion object {
        private const val KEY_TELEMETRY_RESTORED = "telemetry_restored"
        private const val KEY_REPLAY_RESUMED = "replay_resumed"
    }
}