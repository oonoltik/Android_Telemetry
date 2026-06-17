package com.alex.android_telemetry.ui.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DashcamTripOwnership {
    NONE,
    VIDEO_IMPLICIT,
    MANUAL,
}

class DashcamTripCoordinator {

    private val _ownership =
        MutableStateFlow(DashcamTripOwnership.NONE)

    val ownership: StateFlow<DashcamTripOwnership> =
        _ownership.asStateFlow()

    fun handleVideoStart(
        isTripActive: Boolean,
        startTrip: () -> Unit,
    ) {
        if (!isTripActive) {
            startTrip()
            _ownership.value = DashcamTripOwnership.VIDEO_IMPLICIT
            return
        }

        if (_ownership.value == DashcamTripOwnership.NONE) {
            _ownership.value = DashcamTripOwnership.MANUAL
        }
    }

    fun handleVideoStop(
        stopTrip: () -> Unit,
    ) {
        if (_ownership.value == DashcamTripOwnership.VIDEO_IMPLICIT) {
            stopTrip()
            _ownership.value = DashcamTripOwnership.NONE
        }
    }

    fun handleManualTripStart(
        startTrip: () -> Unit,
        stopTrip: () -> Unit,
    ) {
        if (_ownership.value == DashcamTripOwnership.VIDEO_IMPLICIT) {
            stopTrip()
        }

        startTrip()
        _ownership.value = DashcamTripOwnership.MANUAL
    }

    fun handleManualTripStop(
        isVideoRecording: Boolean,
        startTrip: () -> Unit,
        stopTrip: () -> Unit,
    ) {
        stopTrip()

        if (isVideoRecording) {
            startTrip()
            _ownership.value = DashcamTripOwnership.VIDEO_IMPLICIT
        } else {
            _ownership.value = DashcamTripOwnership.NONE
        }
    }

    fun syncRuntimeState(
        isTripActive: Boolean,
        isVideoRecording: Boolean,
    ) {
        if (!isTripActive && !isVideoRecording) {
            _ownership.value = DashcamTripOwnership.NONE
            return
        }

        if (isTripActive && _ownership.value == DashcamTripOwnership.NONE) {
            _ownership.value = DashcamTripOwnership.MANUAL
        }
    }
}

object DashcamTripCoordinatorHolder {
    val instance: DashcamTripCoordinator =
        DashcamTripCoordinator()
}