package com.alex.android_telemetry.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.alex.android_telemetry.core.recovery.BackgroundRestrictionDetector
import com.alex.android_telemetry.core.recovery.BackgroundRestrictionSnapshot
import android.content.ComponentName

@Composable
fun PermissionsBackgroundScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            refreshKey += 1
        }


    val snapshot = remember(refreshKey) {
        PermissionBackgroundSnapshot.from(context)
    }

    val backgroundRestriction = remember(refreshKey) {
        BackgroundRestrictionDetector.detect(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Permissions / Background",
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onBack) {
                Text("Назад")
            }
        }

        PermissionStatusCard(snapshot)

        BackgroundRestrictionCard(backgroundRestriction)

        OemGuidanceCard(
            snapshot = backgroundRestriction,
            onOpenBatterySettings = { context.openBatteryOptimizationSettings() },
            onOpenAppBatterySettings = { context.openAppBatterySettings() },
            onOpenVendorAutostartSettings = { context.openVendorAutostartSettings() },
        )

        PermissionActionsCard(
            onRefresh = { refreshKey += 1 },
            onOpenAppSettings = { context.openAppSettings() },
            onOpenLocationSettings = { context.openLocationSettings() },
            onOpenBatteryOptimizationSettings = { context.openBatteryOptimizationSettings() },
            onRequestIgnoreBatteryOptimization = { context.requestIgnoreBatteryOptimization() },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            },
        )
    }
}

@Composable
private fun PermissionStatusCard(
    snapshot: PermissionBackgroundSnapshot,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Current status",
                style = MaterialTheme.typography.titleMedium,
            )

            StatusLine("Fine location", snapshot.fineLocation)
            StatusLine("Coarse location", snapshot.coarseLocation)
            StatusLine("Background location", snapshot.backgroundLocation)
            StatusLine("Notifications", snapshot.notifications)
            StatusLine("Location services", snapshot.locationServicesEnabled)
            StatusLine("Battery unrestricted", snapshot.batteryUnrestricted)
            StatusLine("SDK", "Android ${Build.VERSION.SDK_INT}")

            if (!snapshot.readyForBackgroundTelemetry) {
                Text("Background telemetry может быть нестабильной. Проверь location, notification и battery settings.")
            } else {
                Text("Background telemetry permissions выглядят готовыми.")
            }
        }
    }
}

@Composable
private fun PermissionActionsCard(
    onRefresh: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
            )

            Button(onClick = onRefresh) {
                Text("Refresh")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Button(onClick = onRequestNotifications) {
                    Text("Request notifications")
                }
            }

            OutlinedButton(onClick = onOpenAppSettings) {
                Text("Open app settings")
            }

            OutlinedButton(onClick = onOpenLocationSettings) {
                Text("Open location settings")
            }

            OutlinedButton(onClick = onOpenBatteryOptimizationSettings) {
                Text("Open battery optimization settings")
            }

            OutlinedButton(onClick = onRequestIgnoreBatteryOptimization) {
                Text("Request unrestricted battery")
            }
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: Boolean,
) {
    Text("$label: ${if (value) "OK" else "MISSING"}")
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Text("$label: $value")
}

@Composable
private fun OemGuidanceCard(
    snapshot: BackgroundRestrictionSnapshot,
    onOpenBatterySettings: () -> Unit,
    onOpenAppBatterySettings: () -> Unit,
    onOpenVendorAutostartSettings: () -> Unit,
) {
    if (!snapshot.isSamsung && !snapshot.isXiaomi && !snapshot.isHuawei) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "OEM background guidance",
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                snapshot.isSamsung -> {
                    Text("Samsung checklist:")
                    Text("• Remove app from Sleeping apps")
                    Text("• Remove app from Deep sleeping apps")
                    Text("• Set battery mode to Unrestricted")
                    Text("• Keep notifications enabled")
                    Text("• Disable aggressive adaptive battery if telemetry stops")
                }

                snapshot.isXiaomi -> {
                    Text("Xiaomi / MIUI checklist:")
                    Text("• Enable Autostart for this app")
                    Text("• Set Battery saver to No restrictions")
                    Text("• Allow background location")
                    Text("• Lock app in recent apps if telemetry stops")
                }

                snapshot.isHuawei -> {
                    Text("Huawei / Honor checklist:")
                    Text("• Allow app launch manually and automatically")
                    Text("• Disable aggressive battery optimization")
                    Text("• Allow background activity")
                    Text("• Keep location and notifications enabled")
                }
            }

            Button(onClick = onOpenAppBatterySettings) {
                Text("Open app battery settings")
            }

            OutlinedButton(onClick = onOpenBatterySettings) {
                Text("Open battery optimization settings")
            }

            OutlinedButton(onClick = onOpenVendorAutostartSettings) {
                Text("Open OEM autostart/background settings")
            }
        }
    }
}
@Composable
private fun BackgroundRestrictionCard(
    snapshot: BackgroundRestrictionSnapshot,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Background restrictions",
                style = MaterialTheme.typography.titleMedium,
            )

            Text("Manufacturer: ${snapshot.manufacturer}")
            Text("Model: ${snapshot.model}")
            Text("Battery unrestricted: ${if (snapshot.isBatteryOptimizationIgnoring) "OK" else "MISSING"}")
            Text("System background restricted: ${if (snapshot.isBackgroundRestricted) "YES" else "NO"}")
            Text(
                "Standby bucket: ${
                    BackgroundRestrictionDetector.standbyBucketLabel(snapshot.standbyBucket)
                }"
            )

            if (snapshot.warnings.isEmpty()) {
                Text("No obvious background restrictions detected.")
            } else {
                Text("Warnings:")
                snapshot.warnings.forEach {
                    Text("• $it")
                }
            }
        }
    }
}

private data class PermissionBackgroundSnapshot(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val locationServicesEnabled: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val readyForBackgroundTelemetry: Boolean
        get() = fineLocation &&
                backgroundLocation &&
                notifications &&
                locationServicesEnabled &&
                batteryUnrestricted

    companion object {
        fun from(context: Context): PermissionBackgroundSnapshot {
            return PermissionBackgroundSnapshot(
                fineLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
                coarseLocation = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
                backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    true
                },
                notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    true
                },
                locationServicesEnabled = context.isLocationServicesEnabled(),
                batteryUnrestricted = context.isIgnoringBatteryOptimizations(),
            )
        }
    }
}

private fun Context.openAppBatterySettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    startActivity(intent)
}

private fun Context.openVendorAutostartSettings() {
    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()

    val candidates = when {
        manufacturer.contains("samsung") -> listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

        manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings",
                ),
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )

        manufacturer.contains("huawei") ||
                manufacturer.contains("honor") -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )

        else -> listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )
    }

    val intent = candidates.firstOrNull { candidate ->
        candidate.resolveActivity(packageManager) != null
    } ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))

    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        openAppSettings()
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
        val gps = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF,
        )
        gps != Settings.Secure.LOCATION_MODE_OFF
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openLocationSettings() {
    startActivity(
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openBatteryOptimizationSettings() {
    startActivity(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.requestIgnoreBatteryOptimization() {
    if (isIgnoringBatteryOptimizations()) return

    startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}