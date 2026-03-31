package com.alex.android_telemetry.telemetry.auth

import android.content.Context

class TelemetryTokenStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("telemetry_auth", Context.MODE_PRIVATE)

    fun getToken(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun getExpiresAt(): String? =
        prefs.getString(KEY_EXPIRES_AT, null)?.takeIf { it.isNotBlank() }

    fun save(token: String, expiresAt: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}