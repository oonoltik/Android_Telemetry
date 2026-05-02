package com.alex.android_telemetry.sensors.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.alex.android_telemetry.sensors.api.GyroscopeSource
import com.alex.android_telemetry.telemetry.math.NumericSanitizer
import com.alex.android_telemetry.telemetry.domain.model.ImuSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.util.Log

class AndroidGyroscopeSource(
    private val sensorManager: SensorManager,
    private val timestampConverter: AndroidSensorTimestampConverter,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME,
) : GyroscopeSource {

    private val mutableSamples = MutableSharedFlow<ImuSample>(extraBufferCapacity = 128)
    override val samples: Flow<ImuSample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var listener: SensorEventListener? = null

    override suspend fun start() {
        if (listener != null) {
            Log.d("TelemetryTrip", "GyroscopeSource.start(): already started")
            return
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        Log.d(
            "TelemetryTrip",
            "GyroscopeSource.start(): gyroscope=${sensor != null}"
        )

        if (sensor == null) {
            Log.w(
                "TelemetryTrip",
                "GyroscopeSource.start(): missing gyroscope, no gyro samples will be emitted"
            )
            return
        }

        val gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                mutableSamples.tryEmit(
                    ImuSample(
                        timestamp = timestampConverter.toInstant(event.timestamp),
                        gyroX = NumericSanitizer.sanitizeDouble(event.values.getOrNull(0)?.toDouble()),
                        gyroY = NumericSanitizer.sanitizeDouble(event.values.getOrNull(1)?.toDouble()),
                        gyroZ = NumericSanitizer.sanitizeDouble(event.values.getOrNull(2)?.toDouble()),
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(gyroListener, sensor, samplingPeriodUs)
        listener = gyroListener

        Log.d("TelemetryTrip", "GyroscopeSource.start(): listener registered")
    }

    override suspend fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}
