package com.alex.android_telemetry.sensors.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.alex.android_telemetry.sensors.api.AccelerometerSource
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import kotlinx.datetime.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

import com.alex.android_telemetry.sensors.api.DeviceStateSource
import com.alex.android_telemetry.telemetry.domain.model.DeviceStateSnapshot
import kotlinx.datetime.Clock





class AndroidAccelerometerSource(
    private val sensorManager: SensorManager,
    private val timestampConverter: AndroidSensorTimestampConverter,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME,
) : AccelerometerSource {

    private val mutableSamples = MutableSharedFlow<ImuSample>(
        extraBufferCapacity = 128,
    )

    override val samples: Flow<ImuSample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var listener: SensorEventListener? = null

    override suspend fun start() {
        if (listener != null) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                mutableSamples.tryEmit(event.toSample(timestampConverter))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(accelListener, sensor, samplingPeriodUs)
        listener = accelListener
    }

    override suspend fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}

private fun SensorEvent.toSample(timestampConverter: AndroidSensorTimestampConverter): ImuSample {
    val timestamp: Instant = timestampConverter.toInstant(this.timestamp)
    return ImuSample(
        timestamp = Clock.System.now(),
        accelX = NumericSanitizer.metricOptional(values.getOrNull(0)?.toDouble()),
        accelY = NumericSanitizer.metricOptional(values.getOrNull(1)?.toDouble()),
        accelZ = NumericSanitizer.metricOptional(values.getOrNull(2)?.toDouble()),
    )
}