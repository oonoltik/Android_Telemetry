package com.alex.android_telemetry.telemetry.capture.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.alex.android_telemetry.core.log.TelemetryLogger
import com.alex.android_telemetry.telemetry.model.TelemetrySampleDraft
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FusedLocationTelemetrySource(
    private val context: Context,
    private val config: LocationCaptureConfig,
    private val sampleMapper: LocationSampleMapper,
    private val logger: TelemetryLogger
) : LocationTelemetrySource {

    private val flow = MutableSharedFlow<TelemetrySampleDraft>(extraBufferCapacity = 128)

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val request by lazy {
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            config.intervalMillis,
        )
            .setMinUpdateIntervalMillis(config.minUpdateIntervalMillis)
            .setMaxUpdateDelayMillis(config.maxUpdateDelayMillis)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            .build()
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                flow.tryEmit(sampleMapper.map(location))
            }
        }
    }

    override fun observeSamples(): Flow<TelemetrySampleDraft> = flow.asSharedFlow()

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        logger.i("FusedLocationSource", "requestLocationUpdates()")

        suspendCancellableCoroutine<Unit> { cont ->
            fusedClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper(),
            )
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWithException(error)
                }
        }
    }

    override suspend fun stop() {
        logger.i("FusedLocationSource", "removeLocationUpdates()")

        suspendCancellableCoroutine<Unit> { cont ->
            fusedClient.removeLocationUpdates(callback)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWithException(error)
                }
        }
    }

    suspend fun emit(location: Location) {
        flow.emit(sampleMapper.map(location))
    }
}