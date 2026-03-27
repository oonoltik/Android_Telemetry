package com.alex.android_telemetry

import android.content.Context
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.os.PowerManager
import com.alex.android_telemetry.sensors.platform.AndroidAccelerometerSource
import com.alex.android_telemetry.sensors.platform.AndroidDeviceStateSource
import com.alex.android_telemetry.sensors.platform.AndroidGyroscopeSource
import com.alex.android_telemetry.sensors.platform.AndroidHeadingSource
import com.alex.android_telemetry.sensors.platform.AndroidLocationSource
import com.alex.android_telemetry.sensors.platform.AndroidNetworkStateSource
import com.alex.android_telemetry.sensors.platform.AndroidSensorTimestampConverter
import com.alex.android_telemetry.telemetry.batching.BatchIdGenerator
import com.alex.android_telemetry.telemetry.batching.BatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchBuilder
import com.alex.android_telemetry.telemetry.batching.TelemetryFrameAssembler
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase
import com.alex.android_telemetry.telemetry.detectors.MotionVectorComputer
import com.alex.android_telemetry.telemetry.ingest.facade.RoomTelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.ingest.mapper.TelemetryBatchDtoMapper
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import com.alex.android_telemetry.telemetry.runtime.InMemoryTripRuntimeStateStore
import com.alex.android_telemetry.telemetry.runtime.StaticThresholdResolver
import com.alex.android_telemetry.telemetry.runtime.TelemetryFacade
import com.alex.android_telemetry.telemetry.runtime.TelemetryOrchestrator
import com.alex.android_telemetry.telemetry.domain.policy.BatchFlushPolicy
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.os.SystemClock

class TelemetryAppGraph private constructor(
    val facade: TelemetryFacade,
    val scheduler: TelemetryDeliveryScheduler,
) {
    companion object {
        @Volatile
        private var instance: TelemetryAppGraph? = null

        fun get(context: Context): TelemetryAppGraph =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): TelemetryAppGraph {
            val database = TelemetryDatabase.get(context)
            val repository = TelemetryOutboxRepository(database.telemetryOutboxDao())
            val scheduler = TelemetryDeliveryScheduler(context)
            val mapper = TelemetryBatchDtoMapper()

            val enqueuer = RoomTelemetryBatchEnqueuer(
                mapper = mapper,
                repository = repository,
                scheduler = scheduler,
            )

            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val timestampConverter = AndroidSensorTimestampConverter(
                nowElapsedRealtimeNsProvider = { SystemClock.elapsedRealtimeNanos() },
            )

            val orchestrator = TelemetryOrchestrator(
                scope = CoroutineScope(Dispatchers.Default),
                deviceIdProvider = { "android-device-id" },
                driverIdProvider = { "driver-123" },
                transportModeProvider = { "car" },
                accelerometerSource = AndroidAccelerometerSource(
                    sensorManager = sensorManager,
                    timestampConverter = timestampConverter,
                ),
                gyroscopeSource = AndroidGyroscopeSource(
                    sensorManager = sensorManager,
                    timestampConverter = timestampConverter,
                ),
                locationSource = AndroidLocationSource(
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(context),
                ),
                headingSource = AndroidHeadingSource(
                    sensorManager = sensorManager,
                    timestampConverter = timestampConverter,
                ),
                deviceStateSource = AndroidDeviceStateSource(
                    context = context,
                    powerManager = powerManager,
                ),
                networkStateSource = AndroidNetworkStateSource(
                    connectivityManager = connectivityManager,
                ),
                thresholdResolver = StaticThresholdResolver(),
                frameAssembler = TelemetryFrameAssembler(),
                motionVectorComputer = MotionVectorComputer(),
                batchBuilder = TelemetryBatchBuilder(
                    flushPolicy = BatchFlushPolicy(
                        maxWindowMs = 15_000,
                        maxFrames = 50,
                    ),
                    batchSequenceStore = BatchSequenceStore(),
                    batchIdGenerator = BatchIdGenerator(),
                ),
                batchEnqueuer = enqueuer,
                runtimeStateStore = InMemoryTripRuntimeStateStore(),
            )

            return TelemetryAppGraph(
                facade = TelemetryFacade(orchestrator),
                scheduler = scheduler,
            )
        }
    }
}