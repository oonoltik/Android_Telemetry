package com.alex.android_telemetry.telemetry.driver

import android.content.SharedPreferences

class DriverIdStore(
    private val prefs: SharedPreferences,
) {
    fun get(): String? {
        return prefs.getString(KEY_DRIVER_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun set(driverId: String) {
        prefs.edit()
            .putString(KEY_DRIVER_ID, driverId.trim())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_DRIVER_ID)
            .apply()
    }

    companion object {
        private const val KEY_DRIVER_ID = "driver_id"
    }
}