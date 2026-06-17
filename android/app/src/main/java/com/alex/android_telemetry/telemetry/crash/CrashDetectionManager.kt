package com.alex.android_telemetry.telemetry.crash

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class CrashDetectionManager(
    context: Context,
) : SensorEventListener {
    private val appContext = context.applicationContext

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val linearAccelerationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val accelerometerSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val sensor =
        linearAccelerationSensor ?: accelerometerSensor

    private val crashThresholdG: Double = 1.2

    private val localCooldownMs: Long =
        2_000L

    private var lastCrashAtMs: Long = 0L

    private var listener: ((CrashEvent) -> Unit)? = null

    private val gravity = FloatArray(3)

    fun start(
        onCrashDetected: (CrashEvent) -> Unit,
    ) {
        listener = onCrashDetected

        sensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(
        event: SensorEvent,
    ) {
        val now = System.currentTimeMillis()

        if (now - lastCrashAtMs < localCooldownMs) {
            return
        }

        val gForce =
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                calculateLinearGForce(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                )
            } else {
                calculateHighPassGForce(event)
            }

        if (gForce > crashThresholdG) {
            lastCrashAtMs = now

            listener?.invoke(
                CrashEvent(
                    detectedAtMs = now,
                    gForce = gForce,
                )
            )
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) = Unit

    private fun calculateLinearGForce(
        x: Float,
        y: Float,
        z: Float,
    ): Double {
        val magnitude =
            sqrt(
                x.toDouble() * x.toDouble() +
                        y.toDouble() * y.toDouble() +
                        z.toDouble() * z.toDouble()
            )

        return magnitude / SensorManager.GRAVITY_EARTH
    }

    private fun calculateHighPassGForce(
        event: SensorEvent,
    ): Double {
        val alpha = 0.8f

        gravity[0] =
            alpha * gravity[0] +
                    (1f - alpha) * event.values[0]

        gravity[1] =
            alpha * gravity[1] +
                    (1f - alpha) * event.values[1]

        gravity[2] =
            alpha * gravity[2] +
                    (1f - alpha) * event.values[2]

        val linearX =
            event.values[0] - gravity[0]

        val linearY =
            event.values[1] - gravity[1]

        val linearZ =
            event.values[2] - gravity[2]

        return calculateLinearGForce(
            x = linearX,
            y = linearY,
            z = linearZ,
        )
    }
}