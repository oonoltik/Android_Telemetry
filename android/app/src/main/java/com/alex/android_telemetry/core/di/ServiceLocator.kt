package com.alex.android_telemetry.core.di

object ServiceLocator {
    lateinit var appContainer: AppContainer
        private set

    fun init(container: AppContainer) {
        appContainer = container
    }
}
