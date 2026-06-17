package com.alex.android_telemetry.ui.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import com.alex.android_telemetry.telemetry.crash.CrashEvent
import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.ImageAnalysis
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.telemetry.dashcam.DashcamTelemetrySessionSnapshotStore

data class DashcamRecordingState(
    val isRecording: Boolean = false,
    val activeCamera: DashcamCameraType = DashcamCameraType.ROAD,
    val recordingStartedAtMs: Long? = null,
    val rollingSessionId: String? = null,
    val segmentIndex: Int = 0,
    val isEmergency: Boolean = false,
    val isSaving: Boolean = false,
    val savingProgressPercent: Int = 0,
    val errorMessage: String? = null,
)

class DashcamRecordingController(
    context: Context,
    private val repository: DashcamVideoRepository,
) {
    private val appContext =
        context

    private val mainExecutor: Executor =
        ContextCompat.getMainExecutor(appContext)

    private val handler =
        Handler(Looper.getMainLooper())

    private val sessionApi =
        DashcamSessionApi(appContext)

    private val sessionScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val segmentDurationMs: Long =
        120_000L

    private val recorder =
        Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.FHD),
            )
            .build()

    val videoCapture: VideoCapture<Recorder> =
        VideoCapture.withOutput(recorder)

    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private val persistentPreview: Preview =
        Preview.Builder()
            .build()

    private val driverMonitoringAnalyzer =
        DriverMonitoringAnalyzer(
            appContext,
        )

    private val driverImageAnalysis: ImageAnalysis =
        ImageAnalysis.Builder()
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
            )
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    mainExecutor,
                    driverMonitoringAnalyzer,
                )
            }

    private var hasPreviewSurface: Boolean = false

    private var activeRecording: Recording? = null
    private var currentOutputFile: File? = null
    private var currentStartedAtMs: Long = 0L

    private var currentCameraType: DashcamCameraType =
        DashcamCameraType.ROAD

    private var rollingSessionId: String? = null
    private var segmentIndex: Int = 0
    private var emergencyMode: Boolean = false
    private var userStopRequested: Boolean = false
    private var emergencyKeepRecordingUntilMs: Long = 0L

    private var driverMonitoringEnabledForRecording: Boolean = false

    private var currentTripId: String? = null
    private var currentSessionId: String? = null

    private var currentDriverId: String? = null
    private var currentDeviceId: String? = null
    private var recordingStartedAtMs: Long = 0L


    private var latestStateCallback: ((DashcamRecordingState) -> Unit)? = null

    fun currentRollingSessionId(): String? {
        return rollingSessionId
    }

    fun currentCameraType(): DashcamCameraType {
        return currentCameraType
    }
    fun setStateCallback(
        onStateChanged: ((DashcamRecordingState) -> Unit)?,
    ) {
        latestStateCallback = onStateChanged

        emitState(
            isRecording = activeRecording != null,
        )
    }
    fun isRecording(): Boolean {
        return activeRecording != null
    }

    fun attachPreviewSurface(
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        hasPreviewSurface = true

        persistentPreview.setSurfaceProvider(
            surfaceProvider,
        )
    }
    fun detachPreviewSurface() {
        hasPreviewSurface = false

        persistentPreview.setSurfaceProvider(null)
    }

    fun startDriverMonitoringOnly() {
        android.util.Log.d(
            "DriverMonitoring",
            "monitoring-only disabled: DMS runs only during driver video recording"
        )

        stopDriverMonitoringOnly()
    }

    fun stopDriverMonitoringOnly() {
        val provider =
            cameraProvider ?: return

        if (activeRecording != null) {
            return
        }

        driverMonitoringEnabledForRecording = false


        provider.unbindAll()

        DriverMonitoringStateStore.updateMode(
            DriverMonitoringMode.OFF,
        )

        android.util.Log.d(
            "DriverMonitoring",
            "monitoring-only stopped; analyzer released"
        )
    }

    fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        provider: ProcessCameraProvider,
        preview: Preview?,
        cameraType: DashcamCameraType,
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.cameraProvider = provider

        if (activeRecording != null) {
            return
        }

        currentCameraType = cameraType

        rebindCameraUseCases()
    }

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        provider: ProcessCameraProvider,
        preview: Preview,
        cameraType: DashcamCameraType,
    ) {
        bindPreview(
            lifecycleOwner = lifecycleOwner,
            provider = provider,
            preview = preview,
            cameraType = cameraType,
        )
    }

    fun detachPreview() {
        detachPreviewSurface()
    }

    fun startRecording(
        tripId: String? = null,
        sessionId: String? = null,
        driverId: String? = null,
        deviceId: String? = null,
        onStateChanged: (DashcamRecordingState) -> Unit,
    ) {
        latestStateCallback = onStateChanged

        if (activeRecording != null) {
            emitState(isRecording = true)
            return
        }

        if (cameraProvider == null || lifecycleOwner == null) {
            onStateChanged(
                DashcamRecordingState(
                    isRecording = false,
                    activeCamera = currentCameraType,
                    errorMessage = "Camera provider is not ready",
                )
            )
            return
        }

        currentTripId = tripId
        currentSessionId = sessionId
        currentDriverId = driverId
        currentDeviceId = deviceId
        recordingStartedAtMs = System.currentTimeMillis()
        userStopRequested = false
        emergencyMode = false
        emergencyKeepRecordingUntilMs = 0L
        driverMonitoringEnabledForRecording =
            currentCameraType == DashcamCameraType.DRIVER

        rollingSessionId = UUID.randomUUID().toString().take(8)
        segmentIndex = 1

        postVideoSessionStart()

        rebindCameraUseCases()

        startSegment()
    }

    fun markCrashDetected(
        event: CrashEvent,
        preCrashMs: Long,
        postCrashMs: Long,
    ) {
        emergencyMode = true

        emergencyKeepRecordingUntilMs =
            maxOf(
                emergencyKeepRecordingUntilMs,
                event.detectedAtMs + postCrashMs,
            )

        val session =
            rollingSessionId

        repository.protectCrashWindow(
            crashAtMs = event.detectedAtMs,
            preCrashMs = preCrashMs,
            postCrashMs = postCrashMs,
            rollingSessionId = session,
        )

        emitState(
            isRecording = activeRecording != null,
        )
    }

    fun rotateSegmentForCrashPackage() {
        android.util.Log.d(
            "DashcamRotation",
            "rotateSegmentForCrashPackage activeRecording=${activeRecording != null} userStopRequested=$userStopRequested segmentIndex=$segmentIndex emergencyMode=$emergencyMode"
        )

        if (activeRecording != null && !userStopRequested) {
            activeRecording?.stop()
        }
    }

    fun stopRecording() {

        driverMonitoringEnabledForRecording = false


        DriverMonitoringStateStore.updateMode(
            DriverMonitoringMode.OFF,
        )

        android.util.Log.d(
            "DriverMonitoring",
            "DMS disabled immediately on stopRecording"
        )
        val now =
            System.currentTimeMillis()

        val mustKeepRecording =
            emergencyKeepRecordingUntilMs > now

        if (mustKeepRecording) {
            val delayMs =
                emergencyKeepRecordingUntilMs - now + 500L

            android.util.Log.d(
                "DashcamRecording",
                "stop deferred for crash post window delayMs=$delayMs keepUntil=$emergencyKeepRecordingUntilMs now=$now"
            )

            userStopRequested = true
            driverMonitoringEnabledForRecording = false

            DriverMonitoringStateStore.updateMode(
                DriverMonitoringMode.OFF,
            )

            handler.removeCallbacks(segmentRotationRunnable)

            emitState(
                isRecording = false,
            )

            handler.postDelayed(
                {
                    activeRecording?.stop()
                },
                delayMs,
            )

            return
        }

        userStopRequested = true
        handler.removeCallbacks(segmentRotationRunnable)

        activeRecording?.stop()
    }

    fun markEmergency() {
        emitState(
            isRecording = activeRecording != null,
        )
    }

    fun release() {
        handler.removeCallbacks(segmentRotationRunnable)

        activeRecording?.close()
        activeRecording = null

        android.util.Log.d(
            "DriverMonitoring",
            "stopRecording completed activeRecording=$activeRecording"
        )

        cameraProvider?.unbindAll()

        driverMonitoringAnalyzer.release()
    }

    private fun startSegment() {
        android.util.Log.d(
            "DashcamRotation",
            "startSegment rollingSessionId=$rollingSessionId segmentIndex=$segmentIndex"
        )


        val session =
            rollingSessionId ?: UUID.randomUUID().toString().take(8)

        val outputFile =
            repository.createSegmentFile(
                cameraType = currentCameraType,
                rollingSessionId = session,
                segmentIndex = segmentIndex,
            )
        android.util.Log.d(
            "DashcamRotation",
            "startSegment output=${outputFile.absolutePath} exists=${outputFile.exists()}"
        )

        currentOutputFile = outputFile
        currentStartedAtMs = System.currentTimeMillis()

        val fileOptions =
            FileOutputOptions.Builder(outputFile)
                .build()

        val hasAudioPermission =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        val pendingRecording: PendingRecording =
            videoCapture.output.prepareRecording(
                appContext,
                fileOptions,
            )

        val recordingWithAudio =
            if (hasAudioPermission) {
                pendingRecording.withAudioEnabled()
            } else {
                pendingRecording
            }

        activeRecording =
            recordingWithAudio.start(
                mainExecutor,
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        emitState(isRecording = true)
                        scheduleSegmentRotation()
                    }

                    is VideoRecordEvent.Finalize -> {

                        android.util.Log.d(
                            "DashcamRotation",
                            "Finalize hasError=${event.hasError()} " +
                                    "error=${event.error} " +
                                    "output=${currentOutputFile?.absolutePath} " +
                                    "exists=${currentOutputFile?.exists()} " +
                                    "size=${currentOutputFile?.length()} " +
                                    "segmentIndex=$segmentIndex"
                        )

                        val hadError =
                            event.hasError()

                        val outputFile =
                            currentOutputFile

                        if (outputFile != null && outputFile.exists() && outputFile.length() > 0L) {
                            finalizeCurrentSegment()
                        } else {
                            android.util.Log.w(
                                "DashcamRotation",
                                "skip register empty segment path=${outputFile?.absolutePath} exists=${outputFile?.exists()} size=${outputFile?.length()} error=${event.error}"
                            )
                        }

                        activeRecording = null
                        currentOutputFile = null

                        if (!userStopRequested) {
                            segmentIndex += 1

                            if (hadError) {
                                android.util.Log.w(
                                    "DashcamRotation",
                                    "Finalize had error=${event.error}; rebinding camera before next segment segmentIndex=$segmentIndex"
                                )

                                handler.removeCallbacks(segmentRotationRunnable)

                                handler.postDelayed(
                                    {
                                        rebindCameraUseCases()
                                        startSegment()
                                    },
                                    500L,
                                )
                            } else {
                                android.util.Log.d(
                                    "DashcamRotation",
                                    "Starting next segment segmentIndex=$segmentIndex cameraType=$currentCameraType"
                                )

                                if (currentCameraType == DashcamCameraType.DRIVER) {
                                    handler.removeCallbacks(segmentRotationRunnable)

                                    handler.postDelayed(
                                        {
                                            android.util.Log.d(
                                                "DashcamRotation",
                                                "Driver camera rebind before next segment segmentIndex=$segmentIndex"
                                            )

                                            rebindCameraUseCases()

                                            handler.postDelayed(
                                                {
                                                    android.util.Log.d(
                                                        "DashcamRotation",
                                                        "Driver camera start after rebind segmentIndex=$segmentIndex"
                                                    )

                                                    startSegment()
                                                },
                                                250L,
                                            )
                                        },
                                        150L,
                                    )
                                } else {
                                    startSegment()
                                }
                            }
                        } else {
                            handler.removeCallbacks(segmentRotationRunnable)

                            driverMonitoringEnabledForRecording = false


                            cameraProvider?.unbindAll()

                            DriverMonitoringStateStore.updateMode(
                                DriverMonitoringMode.OFF,
                            )

                            emitState(isRecording = false)

                            postFinalSessionStopAndCameraLog(
                                stoppedAtMs = System.currentTimeMillis(),
                                stopReason = "user_stop",
                            )

                            currentTripId = null
                            currentSessionId = null
                            rollingSessionId = null
                            segmentIndex = 0
                            emergencyMode = false
                            emergencyKeepRecordingUntilMs = 0L

                            android.util.Log.d(
                                "DriverMonitoring",
                                "stopped after video stop: camera unbound, DMS OFF"
                            )
                        }
                    }
                }
            }
    }

    private val segmentRotationRunnable =
        Runnable {
            if (activeRecording != null && !userStopRequested) {
                activeRecording?.stop()
            }
        }

    private fun scheduleSegmentRotation() {
        handler.removeCallbacks(segmentRotationRunnable)

        handler.postDelayed(
            segmentRotationRunnable,
            segmentDurationMs,
        )
    }

    private fun finalizeCurrentSegment() {
        val file =
            currentOutputFile ?: return

        if (!file.exists()) {
            return
        }

        repository.registerVideo(
            file = file,
            cameraType = currentCameraType,
            startedAtMs = currentStartedAtMs,
            endedAtMs = System.currentTimeMillis(),
            isEmergency = false,
            isProtected = false,
            tripId = currentTripId,
            sessionId = currentSessionId,
            rollingSessionId = rollingSessionId,
            segmentIndex = segmentIndex,
        )
    }

    private fun rebindCameraUseCases() {
        val provider =
            cameraProvider ?: return

        val owner =
            lifecycleOwner ?: return

        provider.unbindAll()

        val selector =
            when (currentCameraType) {
                DashcamCameraType.ROAD ->
                    CameraSelector.DEFAULT_BACK_CAMERA

                DashcamCameraType.DRIVER ->
                    CameraSelector.DEFAULT_FRONT_CAMERA
            }

//        val shouldEnableDriverMonitoring = false

        val shouldEnableDriverMonitoring =
            currentCameraType == DashcamCameraType.DRIVER &&
                    driverMonitoringEnabledForRecording

        if (!shouldEnableDriverMonitoring) {
            provider.bindToLifecycle(
                owner,
                selector,
                persistentPreview,
                videoCapture,
            )

            DriverMonitoringStateStore.updateMode(
                DriverMonitoringMode.OFF,
            )

            android.util.Log.d(
                "DriverMonitoring",
                "mode=OFF Preview+VideoCapture only cameraType=$currentCameraType recordingDms=$driverMonitoringEnabledForRecording"
            )

            return
        }

        try {
            provider.bindToLifecycle(
                owner,
                selector,
                persistentPreview,
                videoCapture,
                driverImageAnalysis,
            )

            DriverMonitoringStateStore.updateMode(
                DriverMonitoringMode.FULL,
            )

            android.util.Log.d(
                "DriverMonitoring",
                "mode=FULL driver camera uses Preview+VideoCapture+ImageAnalysis"
            )
        } catch (error: Throwable) {
            android.util.Log.w(
                "DriverMonitoring",
                "mode=SAFE fallback: Preview+VideoCapture+ImageAnalysis failed",
                error,
            )

            provider.unbindAll()

            provider.bindToLifecycle(
                owner,
                selector,
                persistentPreview,
                videoCapture,
            )

            DriverMonitoringStateStore.updateMode(
                DriverMonitoringMode.SAFE,
            )

            android.util.Log.d(
                "DriverMonitoring",
                "mode=SAFE driver camera uses Preview+VideoCapture only"
            )
        }
    }

    private fun emitState(
        isRecording: Boolean,
    ) {
        latestStateCallback?.invoke(
            DashcamRecordingState(
                isRecording = isRecording,
                activeCamera = currentCameraType,
                recordingStartedAtMs =
                    if (isRecording) {
                        currentStartedAtMs
                    } else {
                        null
                    },
                rollingSessionId = rollingSessionId,
                segmentIndex = segmentIndex,
                isEmergency = emergencyMode,
            )
        )
    }

    private fun postFinalSessionStopAndCameraLog(
        stoppedAtMs: Long,
        stopReason: String,
    ) {
        val session =
            rollingSessionId ?: return

        val driver =
            currentDriverId?.trim().orEmpty()

        val device =
            currentDeviceId?.trim().orEmpty()

        if (driver.isBlank() || device.isBlank()) {
            Log.w(
                "DashcamSessionApi",
                "skip session stop/camera-log: driverId or deviceId is blank session=$session",
            )
            return
        }

        val segments =
            repository
                .loadVideos()
                .filter { item ->
                    item.rollingSessionId == session
                }
                .sortedBy { item ->
                    item.segmentIndex
                }

        val totalSizeBytes =
            segments.sumOf { item ->
                item.sizeBytes
            }

        val normalSegments =
            repository
                .loadRegularVideos()

        val crashSegments =
            repository
                .loadEmergencyVideos()

        val cameraMode =
            when (currentCameraType) {
                DashcamCameraType.ROAD -> "road"
                DashcamCameraType.DRIVER -> "driver"
            }

        val startedAtMs =
            segments.minOfOrNull { item ->
                item.startedAtMs
            } ?: recordingStartedAtMs

        val endedAtMs =
            segments.maxOfOrNull { item ->
                item.endedAtMs
            } ?: stoppedAtMs

        val firstSegment =
            segments.firstOrNull()

        val lastSegment =
            segments.lastOrNull()

        val telemetrySnapshot =
            DashcamTelemetrySessionSnapshotStore.snapshot(
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
            )

        sessionScope.launch {
            try {
                sessionApi.stopVideoSession(
                    VideoSessionStopPayload(
                        video_session_id = session,
                        ended_at = DashcamSessionApi.isoUtc(endedAtMs),
                        stop_reason = stopReason,
                        final_linked_trip_session_id = currentSessionId,
                        segments_count = segments.size,
                        total_size_bytes = totalSizeBytes,
                    )
                )

                sessionApi.postCameraLog(
                    DashcamCameraLogPayload(
                        video_session_id = session,
                        linked_trip_session_id = currentSessionId,
                        driver_id = driver,
                        device_id = device,
                        started_at = DashcamSessionApi.isoUtc(startedAtMs),
                        ended_at = DashcamSessionApi.isoUtc(endedAtMs),

                        recording_start_lat =
                            telemetrySnapshot.recordingStartLat ?: firstSegment?.gpsStartLat,

                        recording_start_lon =
                            telemetrySnapshot.recordingStartLon ?: firstSegment?.gpsStartLon,

                        recording_end_lat =
                            telemetrySnapshot.recordingEndLat ?: lastSegment?.gpsEndLat,

                        recording_end_lon =
                            telemetrySnapshot.recordingEndLon ?: lastSegment?.gpsEndLon,

                        session_start_sample_t =
                            telemetrySnapshot.sessionStartSampleT,

                        session_end_sample_t =
                            telemetrySnapshot.sessionEndSampleT,

                        total_samples =
                            telemetrySnapshot.totalSamples,

                        total_events =
                            telemetrySnapshot.totalEvents,

                        session_start_speed_kmh =
                            telemetrySnapshot.sessionStartSpeedKmh ?: firstSegment?.speedStartKmh,

                        session_end_speed_kmh =
                            telemetrySnapshot.sessionEndSpeedKmh ?: lastSegment?.speedEndKmh,

                        session_event_types =
                            telemetrySnapshot.sessionEventTypes,

                        stop_reason = stopReason,
                        camera_mode = cameraMode,
                        audio_enabled = true,

                        is_crash_log = false,

                        total_size_bytes = totalSizeBytes,
                        total_segments_count = segments.size,

                        archive_normal_count = normalSegments.size,
                        archive_crash_count = crashSegments.size,
                        archive_normal_size_bytes = normalSegments.sumOf { item ->
                            item.sizeBytes
                        },
                        archive_crash_size_bytes = crashSegments.sumOf { item ->
                            item.sizeBytes
                        },
                    )
                )

                Log.d(
                    "DashcamSessionApi",
                    "session stop + camera-log posted session=$session segments=${segments.size}",
                )
            } catch (error: Throwable) {
                Log.e(
                    "DashcamSessionApi",
                    "session stop/camera-log failed session=$session: ${error.message}",
                    error,
                )
            }
        }
    }
    private fun postVideoSessionStart() {
        val session =
            rollingSessionId ?: return

        val driver =
            currentDriverId?.trim().orEmpty()

        val device =
            currentDeviceId?.trim().orEmpty()

        if (driver.isBlank() || device.isBlank()) {
            Log.w(
                "DashcamSessionApi",
                "skip session/start: driverId or deviceId is blank session=$session",
            )
            return
        }

        val cameraMode =
            when (currentCameraType) {
                DashcamCameraType.ROAD -> "road"
                DashcamCameraType.DRIVER -> "driver"
            }

        val startedAtMs =
            if (recordingStartedAtMs > 0L) {
                recordingStartedAtMs
            } else {
                System.currentTimeMillis()
            }

        sessionScope.launch {
            try {
                sessionApi.startVideoSession(
                    VideoSessionStartPayload(
                        video_session_id = session,
                        device_id = device,
                        driver_id = driver,
                        started_at = DashcamSessionApi.isoUtc(startedAtMs),
                        linked_trip_session_id = currentSessionId,
                        trip_source = "dashcam",
                        camera_mode = cameraMode,
                        audio_enabled = true,
                        app_version = "android-${BuildConfig.VERSION_NAME}",
                        device_model = android.os.Build.MODEL,
                    )
                )

                Log.d(
                    "DashcamSessionApi",
                    "session/start posted session=$session",
                )
            } catch (error: Throwable) {
                Log.e(
                    "DashcamSessionApi",
                    "session/start failed session=$session: ${error.message}",
                    error,
                )
            }
        }
    }


}