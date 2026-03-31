package com.alex.android_telemetry.telemetry.runtime

import com.alex.android_telemetry.sensors.api.AccelerometerSource
import com.alex.android_telemetry.sensors.api.DeviceStateSource
import com.alex.android_telemetry.sensors.api.GyroscopeSource
import com.alex.android_telemetry.sensors.api.HeadingSource
import com.alex.android_telemetry.sensors.api.LocationSource
import com.alex.android_telemetry.sensors.api.NetworkStateSource
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.domain.model.LocationFix
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Не production implementation.
 * Это пример формы Android adapters, куда потом подключаются
 * SensorManager, FusedLocationProviderClient, BroadcastReceiver и т.д.
 */
class StubAccelerometerSource : AccelerometerSource {
    private val mutable = MutableSharedFlow<ImuSample>(extraBufferCapacity = 32)
    override val samples: Flow<ImuSample> = mutable.asSharedFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
}

class StubGyroscopeSource : GyroscopeSource {
    private val mutable = MutableSharedFlow<ImuSample>(extraBufferCapacity = 32)
    override val samples: Flow<ImuSample> = mutable.asSharedFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
}

class StubLocationSource : LocationSource {
    private val mutable = MutableSharedFlow<LocationFix>(extraBufferCapacity = 32)
    override val fixes: Flow<LocationFix> = mutable.asSharedFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
}

class StubHeadingSource : HeadingSource {
    private val mutable = MutableSharedFlow<HeadingSample>(extraBufferCapacity = 8)
    override val samples: Flow<HeadingSample> = mutable.asSharedFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
}

class StubDeviceStateSource : DeviceStateSource {
    private val mutable = MutableSharedFlow<DeviceStateSnapshot>(replay = 1, extraBufferCapacity = 8)
    override val snapshots: Flow<DeviceStateSnapshot> = mutable.asSharedFlow()
}

class StubNetworkStateSource : NetworkStateSource {
    private val mutable = MutableSharedFlow<NetworkStateSnapshot>(replay = 1, extraBufferCapacity = 8)
    override val snapshots: Flow<NetworkStateSnapshot> = mutable.asSharedFlow()
}
