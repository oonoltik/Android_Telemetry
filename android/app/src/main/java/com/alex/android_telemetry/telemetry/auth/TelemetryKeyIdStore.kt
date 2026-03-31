package com.alex.android_telemetry.telemetry.auth

import android.content.Context
import java.util.UUID

class TelemetryKeyIdStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("telemetry_auth", Context.MODE_PRIVATE)

    fun getOrCreate(deviceId: String): String {
        val existing = prefs.getString(KEY_KEY_ID, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val created = "android-$deviceId-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_KEY_ID, created).apply()
        return created
    }

    fun clear() {
        prefs.edit().remove(KEY_KEY_ID).apply()
    }

    companion object {
        private const val KEY_KEY_ID = "key_id"
    }
}