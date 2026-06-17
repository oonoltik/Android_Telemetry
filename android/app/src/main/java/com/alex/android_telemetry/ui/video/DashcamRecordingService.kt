package com.alex.android_telemetry.ui.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.alex.android_telemetry.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashcamRecordingService : LifecycleService() {
    private lateinit var controller: DashcamRecordingController

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        controller =
            DashcamRecordingControllerHost.get(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                val cameraType =
                    runCatching {
                        DashcamCameraType.valueOf(
                            intent.getStringExtra(EXTRA_CAMERA_TYPE)
                                ?: DashcamCameraType.ROAD.name,
                        )
                    }.getOrDefault(
                        DashcamCameraType.ROAD,
                    )

                val driverId =
                    intent.getStringExtra(EXTRA_DRIVER_ID)

                val deviceId =
                    intent.getStringExtra(EXTRA_DEVICE_ID)

                val tripSessionId =
                    intent.getStringExtra(EXTRA_TRIP_SESSION_ID)

                startForeground(
                    NOTIFICATION_ID,
                    buildRecordingNotification(),
                )

                startServiceRecording(
                    cameraType = cameraType,
                    driverId = driverId,
                    deviceId = deviceId,
                    tripSessionId = tripSessionId,
                )
            }

            ACTION_STOP -> {
                lifecycleScope.launch {
                    DashcamRecordingStateStore.update(
                        DashcamRecordingState(
                            isRecording = false,
                            activeCamera = controller.currentCameraType(),
                            isSaving = true,
                            savingProgressPercent = 0,
                        )
                    )

                    controller.stopRecording()

                    listOf(15, 35, 55, 75, 90, 100).forEach { progress ->
                        delay(250)

                        DashcamRecordingStateStore.update(
                            DashcamRecordingState(
                                isRecording = false,
                                activeCamera = controller.currentCameraType(),
                                isSaving = true,
                                savingProgressPercent = progress,
                            )
                        )
                    }

                    delay(350)

                    DashcamRecordingStateStore.reset()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startServiceRecording(
        cameraType: DashcamCameraType,
        driverId: String?,
        deviceId: String?,
        tripSessionId: String?,
    ) {
        if (controller.isRecording()) {
            return
        }

        val providerFuture =
            ProcessCameraProvider.getInstance(this)

        providerFuture.addListener(
            {
                val provider =
                    providerFuture.get()

                controller.bindPreview(
                    lifecycleOwner = this,
                    provider = provider,
                    preview = null,
                    cameraType = cameraType,
                )

                controller.startRecording(
                    tripId = tripSessionId,
                    sessionId = tripSessionId,
                    driverId = driverId,
                    deviceId = deviceId,
                ) { state ->
                    DashcamRecordingStateStore.update(state)

                    if (!state.isRecording && state.errorMessage != null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun buildRecordingNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.dashcam_recording_notification_title))
            .setContentText(getString(R.string.dashcam_recording_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dashcam_recording_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.dashcam_recording_channel_description)
                setShowBadge(false)
            }

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "dashcam_recording"
        private const val NOTIFICATION_ID = 5101

        private const val EXTRA_CAMERA_TYPE =
            "extra_camera_type"

        private const val EXTRA_DRIVER_ID =
            "extra_driver_id"

        private const val EXTRA_DEVICE_ID =
            "extra_device_id"

        private const val EXTRA_TRIP_SESSION_ID =
            "extra_trip_session_id"

        const val ACTION_START =
            "com.alex.android_telemetry.dashcam.START"

        const val ACTION_STOP =
            "com.alex.android_telemetry.dashcam.STOP"

        fun start(
            context: Context,
            cameraType: DashcamCameraType,
            driverId: String?,
            deviceId: String?,
            tripSessionId: String?,
        ) {
            val intent =
                Intent(
                    context,
                    DashcamRecordingService::class.java,
                ).apply {
                    action = ACTION_START

                    putExtra(
                        EXTRA_CAMERA_TYPE,
                        cameraType.name,
                    )

                    putExtra(
                        EXTRA_DRIVER_ID,
                        driverId,
                    )

                    putExtra(
                        EXTRA_DEVICE_ID,
                        deviceId,
                    )

                    putExtra(
                        EXTRA_TRIP_SESSION_ID,
                        tripSessionId,
                    )
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(
            context: Context,
        ) {
            val intent =
                Intent(
                    context,
                    DashcamRecordingService::class.java,
                ).apply {
                    action = ACTION_STOP
                }

            context.startService(intent)
        }
    }
}