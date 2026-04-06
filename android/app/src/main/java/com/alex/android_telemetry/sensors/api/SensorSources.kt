package com.alex.android_telemetry.sensors.api

import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSample
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.PedometerSample
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionSample
import kotlinx.coroutines.flow.Flow

interface AccelerometerSource {
    val samples: Flow<ImuSample>
    suspend fun start()
    suspend fun stop()
}

interface GyroscopeSource {
    val samples: Flow<ImuSample>
    suspend fun start()
    suspend fun stop()
}

interface LocationSource {
    val fixes: Flow<LocationFix>
    suspend fun start()
    suspend fun stop()
}

interface HeadingSource {
    val samples: Flow<HeadingSample>
    suspend fun start()
    suspend fun stop()
}

interface DeviceStateSource {
    val snapshots: Flow<DeviceStateSnapshot>
}

interface NetworkStateSource {
    val snapshots: Flow<NetworkStateSnapshot>
}

interface ActivityRecognitionSource {
    val samples: Flow<ActivitySample>
    suspend fun start()
    suspend fun stop()
}

interface PedometerSource {
    val samples: Flow<PedometerSample>
    suspend fun start()
    suspend fun stop()
}

interface AltimeterSource {
    val samples: Flow<AltimeterSample>
    suspend fun start()
    suspend fun stop()
}

interface ScreenInteractionSource {
    val samples: Flow<ScreenInteractionSample>
    suspend fun start()
    suspend fun stop()
}