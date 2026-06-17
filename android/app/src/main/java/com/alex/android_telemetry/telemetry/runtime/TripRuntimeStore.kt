package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import kotlinx.coroutines.flow.StateFlow

interface TripRuntimeStore {
    val state: StateFlow<TripRuntimeState>
    fun currentState(): TripRuntimeState
    fun setState(newState: TripRuntimeState)
    fun update(transform: (TripRuntimeState) -> TripRuntimeState)
    fun reset()
}