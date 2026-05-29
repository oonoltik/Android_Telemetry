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

data class DashcamRecordingState(
    val isRecording: Boolean = false,
    val activeCamera: DashcamCameraType = DashcamCameraType.ROAD,
    val recordingStartedAtMs: Long? = null,
    val rollingSessionId: String? = null,
    val segmentIndex: Int = 0,
    val isEmergency: Boolean = false,
    val errorMessage: String? = null,
)

class DashcamRecordingController(
    context: Context,
    private val repository: DashcamVideoRepository,
) {
    private val appContext = context.applicationContext

    private val mainExecutor: Executor =
        ContextCompat.getMainExecutor(appContext)

    private val handler =
        Handler(Looper.getMainLooper())

    private val segmentDurationMs: Long =
        10_000L

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

    private var currentTripId: String? = null
    private var currentSessionId: String? = null

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
        userStopRequested = false
        emergencyMode = false
        rollingSessionId = UUID.randomUUID().toString().take(8)
        segmentIndex = 1

        rebindCameraUseCases()

        startSegment()
    }

    fun markCrashDetected(
        event: CrashEvent,
        preCrashMs: Long,
        postCrashMs: Long,
    ) {
        emergencyMode = true

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
        if (activeRecording != null && !userStopRequested) {
            activeRecording?.stop()
        }
    }

    fun stopRecording() {
        userStopRequested = true
        handler.removeCallbacksAndMessages(null)

        activeRecording?.stop()
    }

    fun markEmergency() {
        emitState(
            isRecording = activeRecording != null,
        )
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)

        activeRecording?.close()
        activeRecording = null

        cameraProvider?.unbindAll()
    }

    private fun startSegment() {
        val session =
            rollingSessionId ?: UUID.randomUUID().toString().take(8)

        val outputFile =
            repository.createSegmentFile(
                cameraType = currentCameraType,
                rollingSessionId = session,
                segmentIndex = segmentIndex,
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
                        finalizeCurrentSegment()

                        activeRecording = null
                        currentOutputFile = null

                        if (!userStopRequested) {
                            segmentIndex += 1
                            startSegment()
                        } else {
                            emitState(isRecording = false)
                            currentTripId = null
                            currentSessionId = null
                        }
                    }
                }
            }
    }

    private fun scheduleSegmentRotation() {
        handler.removeCallbacksAndMessages(null)

        handler.postDelayed(
            {
                if (activeRecording != null && !userStopRequested) {
                    activeRecording?.stop()
                }
            },
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

        provider.bindToLifecycle(
            owner,
            selector,
            persistentPreview,
            videoCapture,
        )
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


}