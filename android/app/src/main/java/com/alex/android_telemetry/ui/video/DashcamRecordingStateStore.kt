package com.alex.android_telemetry.ui.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DashcamRecordingStateStore {
    private val _state =
        MutableStateFlow(
            DashcamRecordingState()
        )

    val state: StateFlow<DashcamRecordingState> =
        _state

    fun update(
        state: DashcamRecordingState,
    ) {
        _state.value = state
    }

    fun reset() {
        _state.value =
            DashcamRecordingState()
    }
}