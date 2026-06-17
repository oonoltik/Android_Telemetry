package com.alex.android_telemetry.core.recovery

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

data class BackgroundRestrictionSnapshot(
    val manufacturer: String,
    val model: String,
    val isSamsung: Boolean,
    val isXiaomi: Boolean,
    val isHuawei: Boolean,
    val isBatteryOptimizationIgnoring: Boolean,
    val isBackgroundRestricted: Boolean,
    val standbyBucket: Int?,
    val warnings: List<String>,
)

object BackgroundRestrictionDetector {

    fun detect(context: Context): BackgroundRestrictionSnapshot {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val manufacturerLower = manufacturer.lowercase()

        val isSamsung = manufacturerLower.contains("samsung")
        val isXiaomi = manufacturerLower.contains("xiaomi") ||
                manufacturerLower.contains("redmi") ||
                manufacturerLower.contains("poco")
        val isHuawei = manufacturerLower.contains("huawei") ||
                manufacturerLower.contains("honor")

        val batteryIgnoring = context.isIgnoringBatteryOptimizationsSafe()
        val backgroundRestricted = context.isBackgroundRestrictedSafe()
        val standbyBucket = context.appStandbyBucketSafe()

        val warnings = buildList {
            if (!batteryIgnoring) {
                add("Battery optimization is enabled")
            }

            if (backgroundRestricted) {
                add("System reports app as background restricted")
            }

            if (standbyBucket != null && standbyBucket >= UsageStatsManager.STANDBY_BUCKET_RARE) {
                add("App standby bucket is restrictive: ${standbyBucketLabel(standbyBucket)}")
            }

            if (isSamsung) {
                add("Samsung may place app into Sleeping / Deep sleeping apps")
            }

            if (isXiaomi) {
                add("MIUI may block background location and autostart")
            }

            if (isHuawei) {
                add("Huawei/Honor may aggressively stop background services")
            }
        }

        return BackgroundRestrictionSnapshot(
            manufacturer = manufacturer,
            model = model,
            isSamsung = isSamsung,
            isXiaomi = isXiaomi,
            isHuawei = isHuawei,
            isBatteryOptimizationIgnoring = batteryIgnoring,
            isBackgroundRestricted = backgroundRestricted,
            standbyBucket = standbyBucket,
            warnings = warnings,
        )
    }

    fun standbyBucketLabel(bucket: Int?): String {
        return when (bucket) {
            null -> "unknown"
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
            5 -> "restricted_by_oem"
            else -> "unknown($bucket)"
        }
    }
}

private fun Context.isIgnoringBatteryOptimizationsSafe(): Boolean {
    return runCatching {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(packageName)
    }.getOrDefault(false)
}

private fun Context.isBackgroundRestrictedSafe(): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isBackgroundRestricted
        } else {
            false
        }
    }.getOrDefault(false)
}

private fun Context.appStandbyBucketSafe(): Int? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usageStats.appStandbyBucket
        } else {
            null
        }
    }.getOrNull()
}