package com.alex.android_telemetry.ui.status

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.core.recovery.BackgroundRestrictionDetector

@Composable
fun RuntimeWarningBanner(
    state: TripRuntimeState,
    currentDriverId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val warnings = remember(
        state.telemetryMode,
        currentDriverId,
    ) {
        RuntimeWarnings.from(
            context = context,
            state = state,
            currentDriverId = currentDriverId,
        )
    }

    if (warnings.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Runtime warnings",
                style = MaterialTheme.typography.titleMedium,
            )

            warnings.forEach { warning ->
                Text("• $warning")
            }
        }
    }
}

private object RuntimeWarnings {

    fun from(
        context: Context,
        state: TripRuntimeState,
        currentDriverId: String?,
    ): List<String> {
        val result = mutableListOf<String>()

        if (currentDriverId.isNullOrBlank()) {
            result += "No driver logged in"
        }

        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            result += "Fine location permission missing"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            result += "Background location permission missing"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            result += "Notifications permission disabled"
        }

        if (!context.isLocationServicesEnabled()) {
            result += "Location services disabled"
        }

        if (!context.isIgnoringBatteryOptimizations()) {
            result += "Battery optimization enabled"
        }

        if (state.telemetryMode == TelemetryMode.IDLE) {
            result += "Telemetry paused"
        }

        val backgroundRestriction = BackgroundRestrictionDetector.detect(context)

        if (backgroundRestriction.isBackgroundRestricted) {
            result += "System background restriction is active"
        }

        if (backgroundRestriction.standbyBucket != null &&
            backgroundRestriction.standbyBucket >= android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE
        ) {
            result += "App standby bucket is restrictive: ${
                BackgroundRestrictionDetector.standbyBucketLabel(backgroundRestriction.standbyBucket)
            }"
        }

        if (backgroundRestriction.isSamsung) {
            result += "Samsung sleeping apps may stop telemetry in background"
        }

        if (backgroundRestriction.isXiaomi) {
            result += "MIUI may block background telemetry unless autostart is allowed"
        }

        if (backgroundRestriction.isHuawei) {
            result += "Huawei/Honor may aggressively stop background telemetry"
        }

        return result
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.isLocationServicesEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        val mode = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF,
        )
        mode != Settings.Secure.LOCATION_MODE_OFF
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}