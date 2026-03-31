package com.alex.android_telemetry.core.time

import android.os.SystemClock
import java.time.Instant

class SystemClockProvider : ClockProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun nowIsoStringUtc(): String = Instant.ofEpochMilli(nowEpochMillis()).toString()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
