package com.alex.android_telemetry.sensors.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.alex.android_telemetry.sensors.api.HeadingSource
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import com.alex.android_telemetry.telemetry.domain.model.HeadingSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI

/**
 * Lightweight heading source based on TYPE_ROTATION_VECTOR.
 * For Android MVP this is enough to expose optional heading without hard-wiring it into runtime.
 */
class AndroidHeadingSource(
    private val sensorManager: SensorManager,
    private val timestampConverter: AndroidSensorTimestampConverter,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI,
) : HeadingSource {

    private val mutableSamples = MutableSharedFlow<HeadingSample>(extraBufferCapacity = 64)
    override val samples: Flow<HeadingSample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var listener: SensorEventListener? = null

    override suspend fun start() {
        if (listener != null) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        val headingListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthRad = orientation[0].toDouble()
                val headingDeg = ((Math.toDegrees(azimuthRad) + 360.0) % 360.0)
                mutableSamples.tryEmit(
                    HeadingSample(
                        timestamp = timestampConverter.toInstant(event.timestamp),
                        magneticHeadingDeg = NumericSanitizer.sanitizeDouble(headingDeg),
                        accuracyDeg = null,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(headingListener, sensor, samplingPeriodUs)
        listener = headingListener
    }

    override suspend fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}
