package com.alex.android_telemetry.telemetry.trips.api

class TripApiException(
    val code: Int,
    message: String,
) : RuntimeException(message)