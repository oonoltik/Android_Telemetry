package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.telemetry.domain.TripStatus

class TripStateMachine {
    fun canTransition(from: TripStatus, to: TripStatus): Boolean {
        if (from == to) return true
        return when (from) {
            TripStatus.Idle -> to == TripStatus.Starting || to == TripStatus.Recovery
            TripStatus.Starting -> to == TripStatus.Active || to == TripStatus.Error || to == TripStatus.Idle
            TripStatus.Active -> to == TripStatus.Stopping || to == TripStatus.Error
            TripStatus.Stopping -> to == TripStatus.Finishing || to == TripStatus.Finished || to == TripStatus.Error
            TripStatus.Finishing -> to == TripStatus.Finished || to == TripStatus.Error
            TripStatus.Finished -> to == TripStatus.Idle || to == TripStatus.Starting
            TripStatus.Recovery -> to == TripStatus.Active || to == TripStatus.Error || to == TripStatus.Idle
            TripStatus.Error -> to == TripStatus.Idle || to == TripStatus.Recovery
        }
    }

    fun transition(state: TripRuntimeState, to: TripStatus, error: String? = null): TripRuntimeState {
        require(canTransition(state.status, to)) { "Invalid transition: ${state.status} -> $to" }
        return state.copy(status = to, lastError = error)
    }
}
