package com.alex.android_telemetry

import android.app.Application
import com.alex.android_telemetry.core.di.AppContainer
import com.alex.android_telemetry.core.di.ServiceLocator
import com.alex.android_telemetry.core.foreground.NotificationChannels

class TelemetryApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext = this)
        ServiceLocator.init(appContainer)
        NotificationChannels.create(this)
        appContainer.tripRecoveryManager.onAppStarted()
    }
}
