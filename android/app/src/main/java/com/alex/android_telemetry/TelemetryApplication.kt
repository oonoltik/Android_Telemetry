package com.alex.android_telemetry

import android.app.Application
import com.alex.android_telemetry.core.di.AppContainer
import com.alex.android_telemetry.core.di.ServiceLocator
import com.alex.android_telemetry.core.foreground.NotificationChannels
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryScheduler
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryScheduler

class TelemetryApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext = this)
        ServiceLocator.init(appContainer)
        NotificationChannels.create(this)
        appContainer.tripRecoveryManager.onAppStarted()

        TelemetryDeliveryScheduler(this).scheduleImmediate()
        TelemetryDeliveryScheduler(this).schedulePeriodic()
        FinishRetryScheduler(this).scheduleImmediate()
    }
}