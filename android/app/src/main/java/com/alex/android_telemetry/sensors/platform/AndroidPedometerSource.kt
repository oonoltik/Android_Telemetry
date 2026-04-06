package com.alex.android_telemetry.sensors.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.alex.android_telemetry.sensors.api.PedometerSource
import com.alex.android_telemetry.telemetry.domain.model.PedometerSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidPedometerSource(
    private val sensorManager: SensorManager,
    private val timestampConverter: AndroidSensorTimestampConverter,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL,
) : PedometerSource {

    private val mutableSamples = MutableSharedFlow<PedometerSample>(extraBufferCapacity = 64)
    override val samples: Flow<PedometerSample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var listener: SensorEventListener? = null

    override suspend fun start() {
        if (listener != null) return

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return

        val localListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                mutableSamples.tryEmit(
                    PedometerSample(
                        timestamp = timestampConverter.toInstant(event.timestamp),
                        steps = event.values.getOrNull(0)?.toInt(),
                        distanceM = null,
                        cadence = null,
                        pace = null,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(localListener, sensor, samplingPeriodUs)
        listener = localListener
    }

    override suspend fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}