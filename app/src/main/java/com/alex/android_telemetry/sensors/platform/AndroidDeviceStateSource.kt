package com.alex.android_telemetry.sensors.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.BatteryManager
import com.alex.android_telemetry.sensors.api.DeviceStateSource
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import kotlinx.datetime.Clock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidDeviceStateSource(
    private val context: Context,
    private val powerManager: PowerManager,
) : DeviceStateSource {

    override val snapshots: Flow<DeviceStateSnapshot> = callbackFlow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val batteryLevel = if (level >= 0 && scale > 0) level.toDouble() / scale.toDouble() else null
                trySend(
                    DeviceStateSnapshot(
                        timestamp = Clock.System.now(),
                        batteryLevel = batteryLevel,
                        batteryState = batteryStatusName(status),
                        lowPowerMode = powerManager.isPowerSaveMode,
                        isCharging = isCharging,
                    ),
                )
            }
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

private fun batteryStatusName(status: Int): String? = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
    BatteryManager.BATTERY_STATUS_FULL -> "full"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
    else -> null
}
