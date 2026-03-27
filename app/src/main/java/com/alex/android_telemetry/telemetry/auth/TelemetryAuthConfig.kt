package com.alex.android_telemetry.telemetry.auth

internal object TelemetryAuthConfig {
    const val PLATFORM = "android"
    const val APP_PACKAGE = "com.alex.android_telemetry"

    // Для текущего backend bypass.
    // Лучше не плодить варианты и использовать именно "stub".
    const val STUB_ATTESTATION_OBJECT_B64 = "stub"
}