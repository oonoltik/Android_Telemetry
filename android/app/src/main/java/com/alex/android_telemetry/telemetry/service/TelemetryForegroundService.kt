package com.alex.android_telemetry.telemetry.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.alex.android_telemetry.TelemetryAppGraph
import com.alex.android_telemetry.core.di.ServiceLocator
import com.alex.android_telemetry.core.foreground.ForegroundIds
import com.alex.android_telemetry.telemetry.domain.FinishReason
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TelemetryForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val appGraph: TelemetryAppGraph by lazy {
        TelemetryAppGraph.get(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(
            ForegroundIds.TELEMETRY_NOTIFICATION_ID,
            ServiceLocator.appContainer.notificationFactory.buildIdleNotification()
        )

        appGraph.dayMonitoringManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TelemetryServiceActions.ACTION_START_TRIP -> handleStartTrip(intent)
            TelemetryServiceActions.ACTION_STOP_TRIP -> handleStopTrip(intent)
            TelemetryServiceActions.ACTION_RECOVER_TRIP -> handleRecoverTrip()

            TelemetryServiceActions.ACTION_ENABLE_DAY_MONITORING -> handleEnableDayMonitoring()
            TelemetryServiceActions.ACTION_DISABLE_DAY_MONITORING -> handleDisableDayMonitoring()
            TelemetryServiceActions.ACTION_AUTO_START_TRIP -> handleAutoStartTrip(intent)
            TelemetryServiceActions.ACTION_AUTO_STOP_TRIP -> handleAutoStopTrip(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        appGraph.dayMonitoringManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStartTrip(intent: Intent) {
        val deviceId = intent.getStringExtra(TelemetryServiceActions.EXTRA_DEVICE_ID).orEmpty()
        val driverId = intent.getStringExtra(TelemetryServiceActions.EXTRA_DRIVER_ID)
        val trackingMode = runCatching {
            TrackingMode.valueOf(
                intent.getStringExtra(TelemetryServiceActions.EXTRA_TRACKING_MODE).orEmpty()
            )
        }.getOrDefault(TrackingMode.SINGLE_TRIP)
        val transportMode = runCatching {
            TransportMode.valueOf(
                intent.getStringExtra(TelemetryServiceActions.EXTRA_TRANSPORT_MODE).orEmpty()
            )
        }.getOrDefault(TransportMode.CAR)

        serviceScope.launch {
            ServiceLocator.appContainer.tripSessionRuntime.startTrip(
                scope = serviceScope,
                deviceId = deviceId,
                driverId = driverId,
                trackingMode = trackingMode,
                transportMode = transportMode
            )

            appGraph.dayMonitoringManager.markTripStoppedFromService()

            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildActiveTripNotification(
                    ServiceLocator.appContainer.tripSessionRuntime.snapshot()
                )
            )
        }
    }

    private fun handleStopTrip(intent: Intent) {
        val reason = runCatching {
            FinishReason.valueOf(
                intent.getStringExtra(TelemetryServiceActions.EXTRA_FINISH_REASON).orEmpty()
            )
        }.getOrDefault(FinishReason.UNKNOWN)

        serviceScope.launch {
            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildStoppingNotification(
                    ServiceLocator.appContainer.tripSessionRuntime.snapshot()
                )
            )

            ServiceLocator.appContainer.tripSessionRuntime.stopTrip(reason)
            appGraph.dayMonitoringManager.markTripStoppedFromService()
            stopSelf()
        }
    }

    private fun handleRecoverTrip() {
        serviceScope.launch {
            ServiceLocator.appContainer.tripSessionRuntime.recoverIfNeeded(serviceScope)

            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildActiveTripNotification(
                    ServiceLocator.appContainer.tripSessionRuntime.snapshot()
                )
            )
        }
    }

    private fun handleEnableDayMonitoring() {
        appGraph.dayMonitoringManager.enable()
        Log.d("TelemetryService", "day monitoring enabled")
    }

    private fun handleDisableDayMonitoring() {
        appGraph.dayMonitoringManager.disable()
        Log.d("TelemetryService", "day monitoring disabled")
    }

    private fun handleAutoStartTrip(intent: Intent) {
        val deviceId = intent.getStringExtra(TelemetryServiceActions.EXTRA_DEVICE_ID).orEmpty()
        val driverId = intent.getStringExtra(TelemetryServiceActions.EXTRA_DRIVER_ID)
        val transportMode = runCatching {
            TransportMode.valueOf(
                intent.getStringExtra(TelemetryServiceActions.EXTRA_TRANSPORT_MODE).orEmpty()
            )
        }.getOrDefault(TransportMode.CAR)

        serviceScope.launch {
            ServiceLocator.appContainer.tripSessionRuntime.startTrip(
                scope = serviceScope,
                deviceId = deviceId,
                driverId = driverId,
                trackingMode = TrackingMode.DAY_MONITORING,
                transportMode = transportMode
            )

            appGraph.dayMonitoringManager.markAutoTripStartedFromService()

            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildActiveTripNotification(
                    ServiceLocator.appContainer.tripSessionRuntime.snapshot()
                )
            )

            Log.d("TelemetryService", "auto trip started")
        }
    }

    private fun handleAutoStopTrip(intent: Intent) {
        val reason = runCatching {
            FinishReason.valueOf(
                intent.getStringExtra(TelemetryServiceActions.EXTRA_FINISH_REASON).orEmpty()
            )
        }.getOrDefault(FinishReason.UNKNOWN)

        serviceScope.launch {
            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildStoppingNotification(
                    ServiceLocator.appContainer.tripSessionRuntime.snapshot()
                )
            )

            ServiceLocator.appContainer.tripSessionRuntime.stopTrip(reason)
            appGraph.dayMonitoringManager.markTripStoppedFromService()

            Log.d("TelemetryService", "auto trip stopped")
            stopSelf()
        }
    }
}