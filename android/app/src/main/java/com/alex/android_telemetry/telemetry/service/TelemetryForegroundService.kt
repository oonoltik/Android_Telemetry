package com.alex.android_telemetry.telemetry.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.alex.android_telemetry.TelemetryAppGraph
import com.alex.android_telemetry.core.di.ServiceLocator
import com.alex.android_telemetry.core.foreground.ForegroundIds
import com.alex.android_telemetry.telemetry.domain.model.TrackingMode
import com.alex.android_telemetry.telemetry.runtime.toSnapshot
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
            TelemetryServiceActions.ACTION_STOP_TRIP -> handleStopTrip()
            TelemetryServiceActions.ACTION_RECOVER_TRIP -> handleRecoverTrip()

            TelemetryServiceActions.ACTION_ENABLE_DAY_MONITORING -> handleEnableDayMonitoring()
            TelemetryServiceActions.ACTION_DISABLE_DAY_MONITORING -> handleDisableDayMonitoring()
            TelemetryServiceActions.ACTION_AUTO_START_TRIP -> handleAutoStartTrip()
            TelemetryServiceActions.ACTION_AUTO_STOP_TRIP -> handleAutoStopTrip()
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
        val trackingMode = runCatching {
            TrackingMode.valueOf(
                intent.getStringExtra(TelemetryServiceActions.EXTRA_TRACKING_MODE).orEmpty()
            )
        }.getOrDefault(TrackingMode.SINGLE_TRIP)

        serviceScope.launch {
            appGraph.facade.startTrip(mode = trackingMode)
            appGraph.dayMonitoringManager.markTripStoppedFromService()

            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildActiveTripNotification(
                    appGraph.facade.observeState().value.toSnapshot()
                )
            )
        }
    }

    private fun handleStopTrip() {
        serviceScope.launch {
            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildStoppingNotification(
                    appGraph.facade.observeState().value.toSnapshot()
                )
            )

            appGraph.facade.stopTrip()
            appGraph.dayMonitoringManager.markTripStoppedFromService()
            stopSelf()
        }
    }

    private fun handleRecoverTrip() {
        serviceScope.launch {
            appGraph.facade.restore()

            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildActiveTripNotification(
                    appGraph.facade.observeState().value.toSnapshot()
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

    private fun handleAutoStartTrip() {
        serviceScope.launch {
            appGraph.facade.startTrip(mode = TrackingMode.DAY_MONITORING)
            appGraph.dayMonitoringManager.markAutoTripStartedFromService()

            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildActiveTripNotification(
                    appGraph.facade.observeState().value.toSnapshot()
                )
            )

            Log.d("TelemetryService", "auto trip started")
        }
    }

    private fun handleAutoStopTrip() {
        serviceScope.launch {
            startForeground(
                ForegroundIds.TELEMETRY_NOTIFICATION_ID,
                ServiceLocator.appContainer.notificationFactory.buildStoppingNotification(
                    appGraph.facade.observeState().value.toSnapshot()
                )
            )

            appGraph.facade.stopTrip()
            appGraph.dayMonitoringManager.markTripStoppedFromService()

            Log.d("TelemetryService", "auto trip stopped")
            stopSelf()
        }
    }
}