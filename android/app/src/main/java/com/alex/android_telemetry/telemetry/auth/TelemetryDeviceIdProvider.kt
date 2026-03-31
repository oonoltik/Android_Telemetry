package com.alex.android_telemetry.telemetry.auth

import android.content.Context
import android.provider.Settings
import java.util.UUID

class TelemetryDeviceIdProvider(
    private val context: Context,
) {
    fun get(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.trim()

        return if (!androidId.isNullOrBlank()) {
            androidId
        } else {
            UUID.randomUUID().toString()
        }
    }
}