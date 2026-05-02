package com.alex.android_telemetry.sensors.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.alex.android_telemetry.sensors.api.AccelerometerSource
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.util.Log
class AndroidAccelerometerSource(
    private val sensorManager: SensorManager,
    private val timestampConverter: AndroidSensorTimestampConverter,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME,
) : AccelerometerSource {

    companion object {
        private const val GRAVITY_MS2 = 9.80665
    }

    private val mutableSamples = MutableSharedFlow<ImuSample>(
        extraBufferCapacity = 128,
    )

    override val samples: Flow<ImuSample> = mutableSamples
        .asSharedFlow()
        .distinctUntilChanged()

    private var listener: SensorEventListener? = null

    private val rotationMatrix = FloatArray(9)
    private var hasRotationMatrix = false

    override suspend fun start() {
        if (listener != null) {
            Log.d("TelemetryTrip", "AccelerometerSource.start(): already started")
            return
        }

        val linearAccelerationSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val rotationVectorSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val accelerometerSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        Log.d(
            "TelemetryTrip",
            "AccelerometerSource.start(): linearAcceleration=${linearAccelerationSensor != null} rotationVector=${rotationVectorSensor != null} accelerometer=${accelerometerSensor != null}"
        )

        val useFallback = linearAccelerationSensor == null || rotationVectorSensor == null

        if (useFallback) {
            Log.w("TelemetryTrip", "AccelerometerSource: using fallback TYPE_ACCELEROMETER")

            if (accelerometerSensor == null) {
                Log.w("TelemetryTrip", "AccelerometerSource: no accelerometer available")
                return
            }
        }

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        hasRotationMatrix = true
                    }

                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        if (!hasRotationMatrix) {
                            Log.d("TelemetryTrip", "AccelerometerSource.onSensorChanged(): waiting for rotation matrix")
                            return
                        }

                        val sample = event.toReferenceFrameSample(
                            timestampConverter = timestampConverter,
                            rotationMatrix = rotationMatrix,
                        )

                        mutableSamples.tryEmit(sample)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        val xG = (event.values.getOrNull(0)?.toDouble() ?: 0.0) / GRAVITY_MS2
                        val yG = (event.values.getOrNull(1)?.toDouble() ?: 0.0) / GRAVITY_MS2
                        val zG = (event.values.getOrNull(2)?.toDouble() ?: 0.0) / GRAVITY_MS2

                        val sample = ImuSample(
                            timestamp = timestampConverter.toInstant(event.timestamp),
                            accelX = NumericSanitizer.metricOptional(xG),
                            accelY = NumericSanitizer.metricOptional(yG),
                            accelZ = NumericSanitizer.metricOptional(zG),
                        )

//                        Log.d("TelemetryTrip", "Accelerometer fallback sample emitted")

                        mutableSamples.tryEmit(sample)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (!useFallback) {
            sensorManager.registerListener(sensorListener, rotationVectorSensor, samplingPeriodUs)
            sensorManager.registerListener(sensorListener, linearAccelerationSensor, samplingPeriodUs)
        } else {
            sensorManager.registerListener(sensorListener, accelerometerSensor, samplingPeriodUs)
        }

        listener = sensorListener

        Log.d("TelemetryTrip", "AccelerometerSource.start(): listeners registered")
    }

    override suspend fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
        hasRotationMatrix = false
    }

    private fun SensorEvent.toReferenceFrameSample(
        timestampConverter: AndroidSensorTimestampConverter,
        rotationMatrix: FloatArray,
    ): ImuSample {
        val timestamp = timestampConverter.toInstant(this.timestamp)

        val xDevMs2 = values.getOrNull(0)?.toDouble() ?: 0.0
        val yDevMs2 = values.getOrNull(1)?.toDouble() ?: 0.0
        val zDevMs2 = values.getOrNull(2)?.toDouble() ?: 0.0

        val eastMs2 =
            (rotationMatrix[0] * xDevMs2) +
                    (rotationMatrix[1] * yDevMs2) +
                    (rotationMatrix[2] * zDevMs2)

        val northMs2 =
            (rotationMatrix[3] * xDevMs2) +
                    (rotationMatrix[4] * yDevMs2) +
                    (rotationMatrix[5] * zDevMs2)

        val upMs2 =
            (rotationMatrix[6] * xDevMs2) +
                    (rotationMatrix[7] * yDevMs2) +
                    (rotationMatrix[8] * zDevMs2)

        val northG = northMs2 / GRAVITY_MS2
        val eastG = eastMs2 / GRAVITY_MS2
        val upG = upMs2 / GRAVITY_MS2

        return ImuSample(
            timestamp = timestamp,
            accelX = NumericSanitizer.metricOptional(northG),
            accelY = NumericSanitizer.metricOptional(eastG),
            accelZ = NumericSanitizer.metricOptional(upG),
        )
    }
}