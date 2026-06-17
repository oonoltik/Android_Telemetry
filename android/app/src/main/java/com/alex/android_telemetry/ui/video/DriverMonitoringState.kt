package com.alex.android_telemetry.ui.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DriverFatigueState {
    NORMAL,
    WARNING,
    CRITICAL,
    DISTRACTED,
    DROWSY,
}

enum class DriverMonitoringMode {
    OFF,
    FULL,
    SAFE,
}

data class DriverMonitoringState(
    val monitoringMode: DriverMonitoringMode = DriverMonitoringMode.OFF,
    val faceDetected: Boolean = false,
    val eyeOpenScore: Float = 0f,
    val smoothedEyeOpenScore: Float = 0f,
    val mouthOpenScore: Float = 0f,
    val isYawning: Boolean = false,
    val eyesClosed: Boolean = false,
    val perclos: Double = 0.0,
    val fatigueScore: Double = 0.0,
    val fatigueState: DriverFatigueState = DriverFatigueState.NORMAL,
    val microsleepActive: Boolean = false,
    val headYawDeg: Float = 0f,
    val headPitchDeg: Float = 0f,
    val headRollDeg: Float = 0f,
    val lookingLeft: Boolean = false,
    val lookingRight: Boolean = false,
    val headDown: Boolean = false,
    val noFaceFrames: Int = 0,
)

object DriverMonitoringStateStore {
    private val mutableState =
        MutableStateFlow(
            DriverMonitoringState()
        )

    val state: StateFlow<DriverMonitoringState> =
        mutableState

    fun updateMode(
        mode: DriverMonitoringMode,
    ) {
        mutableState.value =
            mutableState.value.copy(
                monitoringMode = mode,
            )
    }

    fun update(
        state: DriverMonitoringState,
    ) {
        mutableState.value =
            state
    }
}