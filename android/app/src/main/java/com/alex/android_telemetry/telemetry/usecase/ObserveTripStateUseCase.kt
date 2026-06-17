package com.alex.android_telemetry.telemetry.usecase

import com.alex.android_telemetry.telemetry.runtime.TripRuntimeSnapshot
import com.alex.android_telemetry.telemetry.runtime.TripRuntimeStore
import com.alex.android_telemetry.telemetry.runtime.toSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTripStateUseCase(
    private val runtimeStore: TripRuntimeStore
) {
    operator fun invoke(): Flow<TripRuntimeSnapshot> =
        runtimeStore.state.map { it.toSnapshot() }
}