package com.alex.android_telemetry

import android.content.Context
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.os.PowerManager
import android.os.SystemClock
import com.alex.android_telemetry.sensors.platform.AndroidAccelerometerSource
import com.alex.android_telemetry.sensors.platform.AndroidDeviceStateSource
import com.alex.android_telemetry.sensors.platform.AndroidGyroscopeSource
import com.alex.android_telemetry.sensors.platform.AndroidHeadingSource
import com.alex.android_telemetry.sensors.platform.AndroidLocationSource
import com.alex.android_telemetry.sensors.platform.AndroidNetworkStateSource
import com.alex.android_telemetry.sensors.platform.AndroidSensorTimestampConverter
import com.alex.android_telemetry.telemetry.auth.TelemetryDeviceIdProvider
import com.alex.android_telemetry.telemetry.batching.BatchIdGenerator
import com.alex.android_telemetry.telemetry.batching.PersistentBatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchBuilder
import com.alex.android_telemetry.telemetry.batching.TelemetryFrameAssembler
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryGraph
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.domain.policy.BatchFlushPolicy
import com.alex.android_telemetry.telemetry.ingest.facade.RoomTelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.ingest.mapper.TelemetryBatchDtoMapper
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import com.alex.android_telemetry.telemetry.runtime.PersistentTripRuntimeStateStore
import com.alex.android_telemetry.telemetry.runtime.StaticThresholdResolver
import com.alex.android_telemetry.telemetry.runtime.TelemetryFacade
import com.alex.android_telemetry.telemetry.runtime.TelemetryOrchestrator
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

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
            val database = com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase.get(context)
            val repository = TelemetryOutboxRepository(database.telemetryOutboxDao())
            val scheduler = TelemetryDeliveryScheduler(context)
            val mapper = TelemetryBatchDtoMapper()

            val appJson = Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            }

            val deliveryGraph = TelemetryDeliveryGraph.from(context)
            val telemetryDeviceIdProvider = TelemetryDeviceIdProvider(context)

            val driverPrefs = context.getSharedPreferences("telemetry_driver", Context.MODE_PRIVATE)
            val driverIdProvider: () -> String? = {
                driverPrefs.getString("driver_id", "analitik7")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }

            val enqueuer = RoomTelemetryBatchEnqueuer(
                mapper = mapper,
                repository = repository,
                scheduler = scheduler,
                json = appJson,
            )

            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val timestampConverter = AndroidSensorTimestampConverter(
                nowElapsedRealtimeNsProvider = { SystemClock.elapsedRealtimeNanos() },
            )

            val batchSequenceStore = PersistentBatchSequenceStore(context)
            val runtimeStateStore = PersistentTripRuntimeStateStore(context)

            val orchestrator = TelemetryOrchestrator(
                scope = CoroutineScope(Dispatchers.Default),
                deviceIdProvider = { telemetryDeviceIdProvider.get() },
                driverIdProvider = driverIdProvider,
                transportModeProvider = { "unknown" },
                tripRepository = deliveryGraph.tripRepository,
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
                motionVectorComputer = com.alex.android_telemetry.telemetry.detectors.MotionVectorComputer(),
                batchBuilder = TelemetryBatchBuilder(
                    flushPolicy = BatchFlushPolicy(
                        maxWindowMs = 15_000,
                        maxFrames = 50,
                    ),
                    batchSequenceStore = batchSequenceStore,
                    batchIdGenerator = BatchIdGenerator(),
                ),
                batchSequenceStore = batchSequenceStore,
                batchEnqueuer = enqueuer,
                outboxRepository = repository,
                runtimeStateStore = runtimeStateStore,
            )

            return TelemetryAppGraph(
                facade = TelemetryFacade(orchestrator),
                scheduler = scheduler,
            )
        }
    }
}