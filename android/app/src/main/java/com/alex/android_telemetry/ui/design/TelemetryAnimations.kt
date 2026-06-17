package com.alex.android_telemetry.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FiniteAnimationSpec

object TelemetryAnimations {
    val FastFade: FiniteAnimationSpec<Float> = tween(durationMillis = 140)
    val StandardFade: FiniteAnimationSpec<Float> = tween(durationMillis = 220)

    fun <T> SwiftSpring(): FiniteAnimationSpec<T> {
        return spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }
}