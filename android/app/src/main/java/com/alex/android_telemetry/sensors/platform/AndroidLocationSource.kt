package com.alex.android_telemetry.sensors.platform

import android.annotation.SuppressLint
import android.location.Location
import com.alex.android_telemetry.sensors.api.LocationSource
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidLocationSource(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationRequestFactory: () -> LocationRequest = {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setWaitForAccurateLocation(false)
            .build()
    },
) : LocationSource {

    private val mutableFixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
    override val fixes: Flow<LocationFix> = mutableFixes.asSharedFlow()

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        if (callback != null) return
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    mutableFixes.tryEmit(location.toFix())
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequestFactory(), newCallback, null)
        callback = newCallback
    }

    override suspend fun stop() {
        callback?.let { fusedLocationClient.removeLocationUpdates(it) }
        callback = null
    }
}

private fun Location.toFix(): LocationFix = LocationFix(
    timestamp = Instant.fromEpochMilliseconds(time),
    lat = latitude,
    lon = longitude,
    horizontalAccuracyM = if (hasAccuracy()) NumericSanitizer.sanitizeDouble(accuracy.toDouble()) else null,
    verticalAccuracyM = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && hasVerticalAccuracy()) {
        NumericSanitizer.sanitizeDouble(verticalAccuracyMeters.toDouble())
    } else null,
    speedMS = if (hasSpeed()) NumericSanitizer.sanitizeDouble(speed.toDouble()) else null,
    speedAccuracyMS = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && hasSpeedAccuracy()) {
        NumericSanitizer.sanitizeDouble(speedAccuracyMetersPerSecond.toDouble())
    } else null,
    bearingDeg = if (hasBearing()) NumericSanitizer.sanitizeDouble(bearing.toDouble()) else null,
    bearingAccuracyDeg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && hasBearingAccuracy()) {
        NumericSanitizer.sanitizeDouble(bearingAccuracyDegrees.toDouble())
    } else null,
    provider = provider,
)
