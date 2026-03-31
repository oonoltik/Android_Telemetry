package com.alex.android_telemetry.telemetry.capture.location

import android.location.Location
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.model.TelemetrySampleDraft

class LocationSampleMapper(
    private val clockProvider: ClockProvider
) {
    fun map(location: Location): TelemetrySampleDraft = TelemetrySampleDraft(
        t = clockProvider.nowIsoStringUtc(),
        lat = location.latitude,
        lon = location.longitude,
        hAcc = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
        vAcc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null,
        speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
        speedAcc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else null,
        course = if (location.hasBearing()) location.bearing.toDouble() else null,
        courseAcc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && location.hasBearingAccuracy()) location.bearingAccuracyDegrees.toDouble() else null
    )
}
