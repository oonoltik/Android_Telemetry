package com.alex.android_telemetry.telemetry.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryTripRuntimeStore : TripRuntimeStore {
    private val mutableState = MutableStateFlow(TripRuntimeState())
    override val state: StateFlow<TripRuntimeState> = mutableState
    override fun currentState(): TripRuntimeState = mutableState.value
    override fun setState(newState: TripRuntimeState) { mutableState.value = newState }
    override fun update(transform: (TripRuntimeState) -> TripRuntimeState) { mutableState.value = transform(mutableState.value) }
    override fun reset() { mutableState.value = TripRuntimeState() }
}
