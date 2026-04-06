package com.alex.android_telemetry.sensors.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.alex.android_telemetry.sensors.api.AltimeterSource
import com.alex.android_telemetry.telemetry.domain.model.AltimeterSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidAltimeterSource(
    private val sensorManager: SensorManager,
    private val timestampConverter: AndroidSensorTimestampConverter,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL,
) : AltimeterSource {

    private val mutableSamples = MutableSharedFlow<AltimeterSample>(extraBufferCapacity = 64)
    override val samples: Flow<AltimeterSample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var listener: SensorEventListener? = null
    private var baselineAltitudeM: Double? = null

    override suspend fun start() {
        if (listener != null) return

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return

        val localListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val pressureHpa = event.values.getOrNull(0)?.toDouble() ?: return
                val absoluteAltitudeM = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    pressureHpa.toFloat(),
                ).toDouble()

                if (baselineAltitudeM == null) {
                    baselineAltitudeM = absoluteAltitudeM
                }

                val relativeAltitudeM = absoluteAltitudeM - (baselineAltitudeM ?: absoluteAltitudeM)

                mutableSamples.tryEmit(
                    AltimeterSample(
                        timestamp = timestampConverter.toInstant(event.timestamp),
                        relativeAltitudeM = relativeAltitudeM,
                        pressureKpa = pressureHpa / 10.0,
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
        baselineAltitudeM = null
    }
}