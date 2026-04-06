package com.alex.android_telemetry.sensors.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.alex.android_telemetry.sensors.api.ActivityRecognitionSource
import com.alex.android_telemetry.telemetry.domain.model.ActivitySample
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock

class AndroidActivityRecognitionSource(
    private val context: Context,
    private val detectionIntervalMillis: Long = 5_000L,
) : ActivityRecognitionSource {

    private val client = ActivityRecognition.getClient(context)
    private val mutableSamples = MutableSharedFlow<ActivitySample>(extraBufferCapacity = 64)
    override val samples: Flow<ActivitySample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var receiver: BroadcastReceiver? = null
    private var started = false

    override suspend fun start() {
        if (started) return

        val filter = IntentFilter(ACTION_ACTIVITY_UPDATES)
        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                if (!ActivityRecognitionResult.hasResult(intent)) return

                val result = ActivityRecognitionResult.extractResult(intent) ?: return
                val mostProbable = result.mostProbableActivity ?: return

                mutableSamples.tryEmit(
                    ActivitySample(
                        timestamp = Clock.System.now(),
                        dominant = mapActivityType(mostProbable.type),
                        confidence = mapConfidence(mostProbable.confidence),
                    )
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                broadcastReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(broadcastReceiver, filter)
        }

        receiver = broadcastReceiver

        try {
            client.requestActivityUpdates(
                detectionIntervalMillis,
                pendingIntent(context),
            ).addOnFailureListener {
                Log.e("TelemetryActivity", "requestActivityUpdates failed", it)
            }
            started = true
        } catch (security: SecurityException) {
            Log.e("TelemetryActivity", "ACTIVITY_RECOGNITION permission missing", security)
            unregisterReceiverQuietly()
        }
    }

    override suspend fun stop() {
        if (!started) return
        client.removeActivityUpdates(pendingIntent(context))
        unregisterReceiverQuietly()
        started = false
    }

    private fun unregisterReceiverQuietly() {
        val localReceiver = receiver ?: return
        runCatching { context.unregisterReceiver(localReceiver) }
        receiver = null
    }

    private fun mapActivityType(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "automotive"
            DetectedActivity.ON_BICYCLE -> "cycling"
            DetectedActivity.ON_FOOT,
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.STILL -> "stationary"
            else -> "unknown"
        }
    }

    private fun mapConfidence(confidence: Int): String {
        return when {
            confidence >= 75 -> "high"
            confidence >= 40 -> "medium"
            else -> "low"
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_ACTIVITY_UPDATES).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags,
        )
    }

    private companion object {
        const val ACTION_ACTIVITY_UPDATES =
            "com.alex.android_telemetry.ACTION_ACTIVITY_UPDATES"
        const val REQUEST_CODE = 4101
    }
}