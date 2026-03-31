package com.alex.android_telemetry.telemetry.model

data class TelemetrySampleDraft(
    val t: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val hAcc: Double? = null,
    val vAcc: Double? = null,
    val speedMps: Double? = null,
    val speedAcc: Double? = null,
    val course: Double? = null,
    val courseAcc: Double? = null
)
