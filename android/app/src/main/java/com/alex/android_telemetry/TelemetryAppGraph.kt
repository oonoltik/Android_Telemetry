package com.alex.android_telemetry

import android.content.Context
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.os.PowerManager
import android.os.SystemClock
import com.alex.android_telemetry.sensors.platform.AndroidAccelerometerSource
import com.alex.android_telemetry.sensors.platform.AndroidActivityRecognitionSource
import com.alex.android_telemetry.sensors.platform.AndroidAltimeterSource
import com.alex.android_telemetry.sensors.platform.AndroidDeviceStateSource
import com.alex.android_telemetry.sensors.platform.AndroidGyroscopeSource
import com.alex.android_telemetry.sensors.platform.AndroidHeadingSource
import com.alex.android_telemetry.sensors.platform.AndroidLocationSource
import com.alex.android_telemetry.sensors.platform.AndroidNetworkStateSource
import com.alex.android_telemetry.sensors.platform.AndroidPedometerSource
import com.alex.android_telemetry.sensors.platform.AndroidScreenInteractionSource
import com.alex.android_telemetry.sensors.platform.AndroidSensorTimestampConverter
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthApi
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import com.alex.android_telemetry.telemetry.auth.TelemetryDeviceIdProvider
import com.alex.android_telemetry.telemetry.auth.TelemetryKeyIdStore
import com.alex.android_telemetry.telemetry.auth.TelemetryTokenStore
import com.alex.android_telemetry.telemetry.batching.BatchFlushPolicy
import com.alex.android_telemetry.telemetry.batching.BatchIdGenerator
import com.alex.android_telemetry.telemetry.batching.LegacyBatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.PersistentLegacyBatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchBuilder
import com.alex.android_telemetry.telemetry.batching.TelemetryFrameAssembler
import com.alex.android_telemetry.telemetry.daymonitoring.ActivityRecognitionTripGate
import com.alex.android_telemetry.telemetry.daymonitoring.DayMonitoringManager
import com.alex.android_telemetry.telemetry.daymonitoring.DayMonitoringStateStore
import com.alex.android_telemetry.telemetry.delivery.TelemetryBackendConfig
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryGraph
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.driver.AccountDeleteManager
import com.alex.android_telemetry.telemetry.driver.DriverIdStore
import com.alex.android_telemetry.telemetry.driver.DriverLoginManager
import com.alex.android_telemetry.telemetry.driver.DriverPrepareManager
import com.alex.android_telemetry.telemetry.driver.DriverRegisterManager
import com.alex.android_telemetry.telemetry.driver.DriverRepository
import com.alex.android_telemetry.telemetry.driver.api.OkHttpDriverApi
import com.alex.android_telemetry.telemetry.ingest.facade.RoomTelemetryBatchEnqueuer
import com.alex.android_telemetry.telemetry.ingest.mapper.TelemetryBatchDtoMapper
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import com.alex.android_telemetry.telemetry.runtime.PersistentTripRuntimeStateStore
import com.alex.android_telemetry.telemetry.runtime.StaticThresholdResolver
import com.alex.android_telemetry.telemetry.runtime.TelemetryFacade
import com.alex.android_telemetry.telemetry.runtime.TelemetryOrchestrator
import com.alex.android_telemetry.telemetry.service.TelemetryServiceStarter
import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.domain.TransportMode
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class TelemetryAppGraph private constructor(
    val facade: TelemetryFacade,
    val scheduler: TelemetryDeliveryScheduler,
    val driverRepository: DriverRepository,
    val driverPrepareManager: DriverPrepareManager,
    val driverRegisterManager: DriverRegisterManager,
    val driverLoginManager: DriverLoginManager,
    val accountDeleteManager: AccountDeleteManager,
    val deviceIdProvider: TelemetryDeviceIdProvider,
    val dayMonitoringManager: DayMonitoringManager,
) {
    companion object {
        @Volatile
        private var instance: TelemetryAppGraph? = null

        fun get(context: Context): TelemetryAppGraph =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): TelemetryAppGraph {
            val applicationScope = CoroutineScope(Dispatchers.Default)

            val database =
                com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase.get(context)

            val repository = TelemetryOutboxRepository(database.telemetryOutboxDao())
            val scheduler = TelemetryDeliveryScheduler(context)
            val mapper = TelemetryBatchDtoMapper()

            val appJson = Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            }

            val okHttpClient = OkHttpClient()

            val telemetryDeviceIdProvider = TelemetryDeviceIdProvider(context)
            val telemetryTokenStore = TelemetryTokenStore(context)
            val telemetryKeyIdStore = TelemetryKeyIdStore(context)

            val telemetryAuthApi = TelemetryAuthApi(
                euBaseUrl = TelemetryBackendConfig.EU_BASE_URL,
                ruBaseUrl = TelemetryBackendConfig.RU_BASE_URL,
                androidRegisterKey = BuildConfig.ANDROID_REGISTER_KEY,
                client = okHttpClient,
                json = appJson,
            )

            val telemetryAuthManager = TelemetryAuthManager(
                authApi = telemetryAuthApi,
                tokenStore = telemetryTokenStore,
                keyIdStore = telemetryKeyIdStore,
                deviceIdProvider = telemetryDeviceIdProvider,
            )

            val deliveryGraph = TelemetryDeliveryGraph.from(context)

            val sharedPreferences =
                context.getSharedPreferences("telemetry_driver", Context.MODE_PRIVATE)

            val driverIdStore = DriverIdStore(sharedPreferences)

            val driverApi = OkHttpDriverApi(
                okHttpClient = okHttpClient,
                json = appJson,
                authManager = telemetryAuthManager,
                euBaseUrl = TelemetryBackendConfig.EU_BASE_URL,
                ruBaseUrl = TelemetryBackendConfig.RU_BASE_URL,
            )

            val driverRepository = DriverRepository(
                driverApi = driverApi,
                driverIdStore = driverIdStore,
            )

            val driverPrepareManager = DriverPrepareManager(
                driverRepository = driverRepository,
            )

            val driverRegisterManager = DriverRegisterManager(
                driverRepository = driverRepository,
            )

            val driverLoginManager = DriverLoginManager(
                driverRepository = driverRepository,
            )

            val accountDeleteManager = AccountDeleteManager(
                driverRepository = driverRepository,
                authManager = telemetryAuthManager,
            )

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

            val activityRecognitionSource = AndroidActivityRecognitionSource(
                context = context,
            )

            val pedometerSource = AndroidPedometerSource(
                sensorManager = sensorManager,
                timestampConverter = timestampConverter,
            )

            val altimeterSource = AndroidAltimeterSource(
                sensorManager = sensorManager,
                timestampConverter = timestampConverter,
            )

            val screenInteractionSource = AndroidScreenInteractionSource(
                context = context,
            )

            val batchSequenceStore: LegacyBatchSequenceStore =
                PersistentLegacyBatchSequenceStore(context)

            val runtimeStateStore = PersistentTripRuntimeStateStore(context)

            val tripDeliveryStatsStore =
                com.alex.android_telemetry.telemetry.trips.storage.TripDeliveryStatsStore(
                    context = context,
                    json = appJson,
                )

            val orchestrator = TelemetryOrchestrator(
                scope = applicationScope,
                deviceIdProvider = { telemetryDeviceIdProvider.get() },
                driverIdProvider = { driverIdStore.get().orEmpty() },
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
                activityRecognitionSource = activityRecognitionSource,
                pedometerSource = pedometerSource,
                altimeterSource = altimeterSource,
                screenInteractionSource = screenInteractionSource,
                thresholdResolver = StaticThresholdResolver(),
                frameAssembler = TelemetryFrameAssembler(),
                motionVectorComputer =
                    com.alex.android_telemetry.telemetry.detectors.MotionVectorComputer(),
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
                tripDeliveryStatsStore = tripDeliveryStatsStore,
                runtimeStateStore = runtimeStateStore,
            )

            val facade = TelemetryFacade(orchestrator)

            val telemetryServiceStarter = TelemetryServiceStarter(context)

            val dayMonitoringStateStore = DayMonitoringStateStore(context)

            val dayMonitoringManager = DayMonitoringManager(
                scope = applicationScope,
                activityRecognitionSource = activityRecognitionSource,
                telemetryFacade = facade,
                stateStore = dayMonitoringStateStore,
                tripGate = ActivityRecognitionTripGate(
                    automotiveStartThresholdSec = 10L,
                    nonAutomotiveStopThresholdSec = 80L,
                ),
                onAutoStartRequested = {
                    telemetryServiceStarter.autoStartTrip(
                        deviceId = telemetryDeviceIdProvider.get(),
                        driverId = driverIdStore.get(),
                        transportMode = TransportMode.CAR,
                    )
                },
                onAutoStopRequested = {
                    telemetryServiceStarter.autoStopTrip(
                        finishReason = FinishReason.UNKNOWN,
                    )
                },
            )

            return TelemetryAppGraph(
                facade = facade,
                scheduler = scheduler,
                driverRepository = driverRepository,
                driverPrepareManager = driverPrepareManager,
                driverRegisterManager = driverRegisterManager,
                driverLoginManager = driverLoginManager,
                accountDeleteManager = accountDeleteManager,
                deviceIdProvider = telemetryDeviceIdProvider,
                dayMonitoringManager = dayMonitoringManager,
            )
        }
    }
}