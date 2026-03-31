package com.alex.android_telemetry.telemetry.auth

import android.content.Context

class TelemetryDriverSessionStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("telemetry_driver_session", Context.MODE_PRIVATE)

    fun getDriverId(): String? {
        return prefs.getString(KEY_DRIVER_ID, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun saveDriverId(driverId: String) {
        prefs.edit().putString(KEY_DRIVER_ID, driverId.trim()).apply()
    }

    fun clearDriverId() {
        prefs.edit().remove(KEY_DRIVER_ID).apply()
    }

    companion object {
        private const val KEY_DRIVER_ID = "driver_id"
    }
}