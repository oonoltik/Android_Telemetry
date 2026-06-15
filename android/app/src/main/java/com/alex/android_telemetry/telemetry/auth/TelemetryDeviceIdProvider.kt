package com.alex.android_telemetry.telemetry.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale
import java.util.TimeZone
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

    fun appVersion(): String {
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                0,
            )

        return packageInfo.versionName ?: "unknown"
    }

    fun appBuild(): String {
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                0,
            )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    }

    fun androidVersionLabel(): String =
        "Android ${Build.VERSION.RELEASE}"

    fun deviceModel(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}"

    fun localeTag(): String =
        Locale.getDefault().toLanguageTag()

    fun timezoneId(): String =
        TimeZone.getDefault().id
}