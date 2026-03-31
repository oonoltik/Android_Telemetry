package com.alex.android_telemetry.telemetry.runtime

import kotlinx.coroutines.flow.StateFlow

interface TripRuntimeStore {
    val state: StateFlow<TripRuntimeState>
    fun currentState(): TripRuntimeState
    fun setState(newState: TripRuntimeState)
    fun update(transform: (TripRuntimeState) -> TripRuntimeState)
    fun reset()
}
