package com.alex.android_telemetry.core.di

import android.content.Context
import com.alex.android_telemetry.core.dispatchers.AppDispatchers
import com.alex.android_telemetry.core.foreground.NotificationFactory
import com.alex.android_telemetry.core.id.BatchIdFactory
import com.alex.android_telemetry.core.id.DefaultBatchIdFactory
import com.alex.android_telemetry.core.id.DefaultSessionIdFactory
import com.alex.android_telemetry.core.id.SessionIdFactory
import com.alex.android_telemetry.core.log.AndroidTelemetryLogger
import com.alex.android_telemetry.core.log.TelemetryLogger
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.core.time.SystemClockProvider
import com.alex.android_telemetry.telemetry.auth.TelemetryDeviceIdProvider
import com.alex.android_telemetry.telemetry.batching.BatchFlushPolicy
import com.alex.android_telemetry.telemetry.batching.BatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.InMemoryBatchSequenceStore
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchAssembler
import com.alex.android_telemetry.telemetry.batching.TelemetryBatchWindow
import com.alex.android_telemetry.telemetry.capture.TelemetryCaptureCoordinator
import com.alex.android_telemetry.telemetry.capture.location.FusedLocationTelemetrySource
import com.alex.android_telemetry.telemetry.capture.location.LocationCaptureConfig
import com.alex.android_telemetry.telemetry.capture.location.LocationSampleMapper
import com.alex.android_telemetry.telemetry.capture.location.LocationTelemetrySource
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryGraph
import com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase
import com.alex.android_telemetry.telemetry.integration.CurrentPipelineBridge
import com.alex.android_telemetry.telemetry.integration.DeliveryFacade
import com.alex.android_telemetry.telemetry.integration.FinishDispatchFacade
import com.alex.android_telemetry.telemetry.integration.RuntimeDeliveryFacade
import com.alex.android_telemetry.telemetry.integration.RuntimeFinishDispatchFacade
import com.alex.android_telemetry.telemetry.recovery.RecoverActiveTripUseCase
import com.alex.android_telemetry.telemetry.recovery.TripRecoveryManager
import com.alex.android_telemetry.telemetry.runtime.InMemoryTripRuntimeStore
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeStore
import com.alex.android_telemetry.telemetry.runtime.TripSessionRuntime
import com.alex.android_telemetry.telemetry.runtime.TripStateMachine
import com.alex.android_telemetry.telemetry.service.TelemetryServiceController
import com.alex.android_telemetry.telemetry.service.TelemetryServiceStarter
import com.alex.android_telemetry.telemetry.session.TripFinishCoordinator
import com.alex.android_telemetry.telemetry.session.TripSessionRepository
import com.alex.android_telemetry.telemetry.session.TripSessionRepositoryImpl
import com.alex.android_telemetry.telemetry.session.TripSessionStarter
import com.alex.android_telemetry.telemetry.session.TripSessionStopper
import com.alex.android_telemetry.telemetry.storage.runtime.ActiveTripDao
import com.alex.android_telemetry.telemetry.storage.runtime.RuntimeStateMapper
import com.alex.android_telemetry.telemetry.usecase.ObserveTripStateUseCase
import com.alex.android_telemetry.telemetry.usecase.StartTripUseCase
import com.alex.android_telemetry.telemetry.usecase.StopTripUseCase

class AppContainer(
    private val applicationContext: Context
) {
    val appContext: Context = applicationContext

    val clockProvider: ClockProvider = SystemClockProvider()
    val appDispatchers: AppDispatchers = AppDispatchers()
    val telemetryLogger: TelemetryLogger = AndroidTelemetryLogger()

    val sessionIdFactory: SessionIdFactory = DefaultSessionIdFactory()
    val batchIdFactory: BatchIdFactory = DefaultBatchIdFactory()

    val notificationFactory: NotificationFactory = NotificationFactory(appContext)
    val runtimeStore: TripRuntimeStore = InMemoryTripRuntimeStore()

    private val mapper = RuntimeStateMapper()

    private val telemetryDatabase: TelemetryDatabase by lazy {
        TelemetryDatabase.get(appContext)
    }

    private val activeTripDao: ActiveTripDao by lazy {
        telemetryDatabase.activeTripDao()
    }

    private val deviceIdProviderStore: TelemetryDeviceIdProvider by lazy {
        TelemetryDeviceIdProvider(appContext)
    }

    private val deliveryGraph: TelemetryDeliveryGraph by lazy {
        TelemetryDeliveryGraph.from(appContext)
    }

    val sessionRepository: TripSessionRepository by lazy {
        TripSessionRepositoryImpl(activeTripDao = activeTripDao, mapper = mapper)
    }

    val batchSequenceStore: BatchSequenceStore = InMemoryBatchSequenceStore()
    val batchWindow: TelemetryBatchWindow = TelemetryBatchWindow()
    val batchFlushPolicy: BatchFlushPolicy = BatchFlushPolicy()
    val batchAssembler: TelemetryBatchAssembler = TelemetryBatchAssembler(clockProvider, batchIdFactory)

    private val locationConfig = LocationCaptureConfig()
    private val locationMapper = LocationSampleMapper(clockProvider)

    val locationTelemetrySource: LocationTelemetrySource = FusedLocationTelemetrySource(
        context = appContext,
        config = locationConfig,
        sampleMapper = locationMapper,
        logger = telemetryLogger
    )

    val deliveryFacade: DeliveryFacade by lazy {
        RuntimeDeliveryFacade(
            context = appContext,
            processor = deliveryGraph.processor,
        )
    }

    val finishDispatchFacade: FinishDispatchFacade by lazy {
        RuntimeFinishDispatchFacade(
            tripRepository = deliveryGraph.tripRepository,
        )
    }

    val pipelineBridge: CurrentPipelineBridge = CurrentPipelineBridge(deliveryFacade, finishDispatchFacade)
    val tripFinishCoordinator: TripFinishCoordinator = TripFinishCoordinator(finishDispatchFacade)

    val captureCoordinator: TelemetryCaptureCoordinator = TelemetryCaptureCoordinator(
        clockProvider = clockProvider,
        logger = telemetryLogger,
        runtimeStore = runtimeStore,
        locationTelemetrySource = locationTelemetrySource,
        batchWindow = batchWindow,
        batchFlushPolicy = batchFlushPolicy,
        batchSequenceStore = batchSequenceStore,
        batchAssembler = batchAssembler,
        deliveryFacade = deliveryFacade
    )

    private val stateMachine = TripStateMachine()

    private val tripSessionStarter: TripSessionStarter by lazy {
        TripSessionStarter(sessionIdFactory, clockProvider, sessionRepository)
    }

    private val tripSessionStopper: TripSessionStopper by lazy {
        TripSessionStopper(clockProvider, tripFinishCoordinator, sessionRepository, runtimeStore)
    }

    val tripSessionRuntime: TripSessionRuntime by lazy {
        TripSessionRuntime(
            logger = telemetryLogger,
            clockProvider = clockProvider,
            runtimeStore = runtimeStore,
            stateMachine = stateMachine,
            sessionRepository = sessionRepository,
            tripSessionStarter = tripSessionStarter,
            tripSessionStopper = tripSessionStopper,
            captureCoordinator = captureCoordinator
        )
    }

    private val telemetryServiceStarter = TelemetryServiceStarter(appContext)

    val serviceController: TelemetryServiceController = TelemetryServiceController(
        serviceStarter = telemetryServiceStarter,
        deviceIdProvider = { deviceIdProviderStore.get() }
    )

    private val recoverActiveTripUseCase = RecoverActiveTripUseCase(serviceController)

    val tripRecoveryManager: TripRecoveryManager by lazy {
        TripRecoveryManager(
            logger = telemetryLogger,
            dispatchers = appDispatchers,
            tripSessionRepository = sessionRepository,
            recoverActiveTripUseCase = recoverActiveTripUseCase
        )
    }

    val startTripUseCase: StartTripUseCase = StartTripUseCase(serviceController)
    val stopTripUseCase: StopTripUseCase = StopTripUseCase(serviceController)
    val observeTripStateUseCase: ObserveTripStateUseCase = ObserveTripStateUseCase(runtimeStore)
}