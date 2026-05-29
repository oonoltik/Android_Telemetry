package com.alex.android_telemetry.ui.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DashcamArchiveRefreshBus {
    private val _version =
        MutableStateFlow(0L)

    val version: StateFlow<Long> =
        _version

    fun notifyChanged() {
        _version.value =
            System.currentTimeMillis()
    }
}