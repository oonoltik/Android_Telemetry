package com.alex.android_telemetry.ui.video

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.alex.android_telemetry.R
import com.alex.android_telemetry.core.localization.AppLanguage
import com.alex.android_telemetry.core.localization.AppLanguageStore
import android.content.res.Configuration
import androidx.annotation.StringRes


class DriverMonitoringAnalyzer(
    context: Context,
) : ImageAnalysis.Analyzer,
    TextToSpeech.OnInitListener {

    private val appContext =
        context

    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_FAST,
                )
                .setLandmarkMode(
                    FaceDetectorOptions.LANDMARK_MODE_NONE,
                )
                .setContourMode(
                    FaceDetectorOptions.CONTOUR_MODE_ALL,
                )
                .setClassificationMode(
                    FaceDetectorOptions.CLASSIFICATION_MODE_ALL,
                )
                .enableTracking()
                .build()
        )

    private val processing =
        AtomicBoolean(false)

    private val tts =
        TextToSpeech(
            appContext,
            this,
        )

    private var ttsReady =
        false

    private var smoothedEyeOpenScore =
        0f

    private var calibratedOpenEye: Float? =
        null

    private val calibrationSamples =
        mutableListOf<Float>()

    private val eyeClosedWindow =
        ArrayDeque<Boolean>()

    private val windowSize =
        150

    private var consecutiveClosedFrames =
        0

    private val microsleepFrames =
        35

    private val emergencyStopFrames =
        90

    private var microsleepActive =
        false

    private var consecutiveOpenFramesAfterMicrosleep =
        0

    private val microsleepRecoveryFrames =
        45

    private var suppressCriticalFramesAfterRecovery =
        0

    private val suppressCriticalFramesAfterRecoveryLimit =
        90

    private var recoveringFromMicrosleep =
        false

    private var faceMissingFrames =
        0

    private val faceMissingFramesThreshold =
        30

    private var distractedFrames =
        0

    private val distractedFramesThreshold =
        30

    private var drowsyFrames =
        0

    private val drowsyFramesThreshold =
        8

    private var candidateState =
        DriverFatigueState.NORMAL

    private var candidateFrameCount =
        0

    private val stateDebounceFrames =
        45

    private var currentFatigueState =
        DriverFatigueState.NORMAL

    private var rawFatigueScore =
        0.0

    private val fatigueScoreRiseStep =
        0.30

    private val fatigueScoreFallStep =
        0.008

    private var fatigueScoreHoldUntilMs =
        0L

    private val fatigueScoreHoldDurationMs =
        60L * 60L * 1000L

    private val lastVoiceAlertAtByKey =
        mutableMapOf<String, Long>()

    private val voiceAlertCooldownMs =
        10_000L

    private val warningPerclosThreshold =
        0.28

    private val criticalPerclosThreshold =
        0.45

    private val normalRecoveryPerclosThreshold =
        0.18

    private val criticalRecoveryPerclosThreshold =
        0.32

    private val minimumClosedEyeThreshold =
        0.35f

    private val closedEyeRatio =
        0.55f

    private val strongClosedEyeThreshold =
        0.18f

    private val yawRightDistractedThresholdDeg =
        18f

    private val yawLeftDistractedThresholdDeg =
        -18f

    private val pitchDownThresholdDeg =
        18f

    private val headPoseCalibrationSamples =
        mutableListOf<Pair<Float, Float>>()

    private var calibratedYawBaseline: Float? =
        null

    private var calibratedPitchBaseline: Float? =
        null

    private var yawnFrames =
        0

    private val yawnFramesThreshold =
        45

    private val yawnMouthOpenThreshold =
        0.40f

    override fun onInit(
        status: Int,
    ) {
        ttsReady =
            status == TextToSpeech.SUCCESS

        if (ttsReady) {
            tts.language =
                currentTtsLocale()

            Log.d(
                "DriverMonitoring",
                "tts init appLanguage=${AppLanguageStore.get(appContext)} locale=${tts.language}",
            )
        }
    }

    private fun currentTtsLocale(): Locale {
        return when (AppLanguageStore.get(appContext)) {
            AppLanguage.Russian -> Locale("ru", "RU")
            AppLanguage.English -> Locale.US
        }
    }

    private fun localizedContext(): Context {
        val locale =
            currentTtsLocale()

        val config =
            Configuration(appContext.resources.configuration)

        config.setLocale(locale)

        return appContext.createConfigurationContext(config)
    }

    private fun ttsText(
        @StringRes resId: Int,
    ): String {
        return localizedContext().getString(resId)
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(
        imageProxy: ImageProxy,
    ) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage =
            imageProxy.image

        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val image =
            InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees,
            )

        detector
            .process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    handleNoFace()
                } else {
                    handleFace(
                        faces.first(),
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.e(
                    "DriverMonitoring",
                    "face detection failed",
                    error,
                )
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    private fun handleFace(
        face: Face,
    ) {
        faceMissingFrames =
            0

        val leftEye =
            face.leftEyeOpenProbability ?: -1f

        val rightEye =
            face.rightEyeOpenProbability ?: -1f

        val eyesProbabilityAvailable =
            leftEye >= 0f && rightEye >= 0f

        val rawEyeOpenScore =
            if (eyesProbabilityAvailable) {
                (leftEye + rightEye) / 2f
            } else {
                1f
            }

        smoothedEyeOpenScore =
            if (smoothedEyeOpenScore == 0f) {
                rawEyeOpenScore
            } else {
                0.85f * smoothedEyeOpenScore + 0.15f * rawEyeOpenScore
            }

        updateCalibrationIfNeeded(
            smoothedEyeOpenScore,
        )

        updateHeadPoseCalibrationIfNeeded(
            yawDeg = -face.headEulerAngleY,
            pitchDeg = face.headEulerAngleX,
        )

        val yawDeg =
            -face.headEulerAngleY - (calibratedYawBaseline ?: 0f)

        val pitchDeg =
            face.headEulerAngleX - (calibratedPitchBaseline ?: 0f)

        val rollDeg =
            face.headEulerAngleZ

        val lookingLeft =
            yawDeg <= yawLeftDistractedThresholdDeg

        val lookingRight =
            yawDeg >= yawRightDistractedThresholdDeg

        val lookingAway =
            lookingLeft || lookingRight

        val headDown =
            pitchDeg >= pitchDownThresholdDeg

        val mouthOpenScore =
            calculateMouthOpenScore(
                face,
            )

        val yawningNow =
            mouthOpenScore >= yawnMouthOpenThreshold

        if (yawningNow) {
            yawnFrames += 1
        } else {
            yawnFrames = 0
        }

        val yawningDetected =
            yawnFrames >= yawnFramesThreshold

        val openEye =
            calibratedOpenEye

        if (openEye == null) {
            val state =
                DriverMonitoringState(
                    faceDetected = true,
                    eyeOpenScore = rawEyeOpenScore,
                    smoothedEyeOpenScore = smoothedEyeOpenScore,
                    mouthOpenScore = mouthOpenScore,
                    isYawning = yawningDetected,
                    fatigueState = DriverFatigueState.NORMAL,
                    headYawDeg = yawDeg,
                    headPitchDeg = pitchDeg,
                    headRollDeg = rollDeg,
                    lookingLeft = lookingLeft,
                    lookingRight = lookingRight,
                    headDown = headDown,
                )

            DriverMonitoringStateStore.update(
                state,
            )

            Log.d(
                "DriverMonitoring",
                "calibrating eye=$rawEyeOpenScore smooth=$smoothedEyeOpenScore samples=${calibrationSamples.size} yaw=$yawDeg pitch=$pitchDeg yawn=$yawningDetected"
            )

            return
        }

        val dynamicClosedThreshold =
            max(
                minimumClosedEyeThreshold,
                openEye * closedEyeRatio,
            )

        val stronglyClosed =
            rawEyeOpenScore < strongClosedEyeThreshold

        val normallyClosed =
            rawEyeOpenScore < dynamicClosedThreshold &&
                    smoothedEyeOpenScore < dynamicClosedThreshold

        val eyeMeasurementReliable =
            eyesProbabilityAvailable &&
                    abs(yawDeg) <= 12f &&
                    abs(pitchDeg) <= 12f &&
                    !yawningDetected

        val effectiveEyesClosed =
            eyeMeasurementReliable &&
                    (stronglyClosed || normallyClosed)

        if (effectiveEyesClosed) {
            consecutiveClosedFrames += 1
            consecutiveOpenFramesAfterMicrosleep = 0

            if (currentFatigueState == DriverFatigueState.CRITICAL) {
                recoveringFromMicrosleep = true
            }
        } else {
            consecutiveClosedFrames = 0
            microsleepActive = false

            if (
                currentFatigueState == DriverFatigueState.CRITICAL ||
                recoveringFromMicrosleep
            ) {
                consecutiveOpenFramesAfterMicrosleep += 1
            } else {
                consecutiveOpenFramesAfterMicrosleep = 0
            }
        }

        if (consecutiveClosedFrames >= microsleepFrames) {
            microsleepActive = true
            recoveringFromMicrosleep = true
            consecutiveOpenFramesAfterMicrosleep = 0
        }

        val emergencyStopActive =
            consecutiveClosedFrames >= emergencyStopFrames

        eyeClosedWindow.addLast(
            effectiveEyesClosed,
        )

        while (eyeClosedWindow.size > windowSize) {
            eyeClosedWindow.removeFirst()
        }

        val perclos =
            eyeClosedWindow.count { it }.toDouble() /
                    max(
                        eyeClosedWindow.size,
                        1,
                    ).toDouble()

        if (lookingAway) {
            distractedFrames += 1
        } else {
            distractedFrames = 0
        }

        if (headDown) {
            drowsyFrames += 1
        } else {
            drowsyFrames = 0
        }

        val isDistracted =
            distractedFrames >= distractedFramesThreshold

        val isDrowsy =
            drowsyFrames >= drowsyFramesThreshold

        val measuredState =
            when {
                emergencyStopActive ->
                    DriverFatigueState.CRITICAL

                microsleepActive ->
                    DriverFatigueState.CRITICAL

                recoveringFromMicrosleep &&
                        consecutiveOpenFramesAfterMicrosleep < microsleepRecoveryFrames ->
                    DriverFatigueState.CRITICAL

                isDrowsy ->
                    DriverFatigueState.DROWSY

                isDistracted ->
                    DriverFatigueState.DISTRACTED

                else ->
                    measuredFatigueState(
                        perclos,
                    )
            }

        val stableState =
            if (
                emergencyStopActive ||
                microsleepActive ||
                (
                        recoveringFromMicrosleep &&
                                consecutiveOpenFramesAfterMicrosleep < microsleepRecoveryFrames
                        )
            ) {
                candidateState =
                    DriverFatigueState.CRITICAL

                candidateFrameCount =
                    stateDebounceFrames

                DriverFatigueState.CRITICAL
            } else {
                updateStateMachine(
                    measuredState,
                )
            }

        currentFatigueState =
            stableState

        if (
            recoveringFromMicrosleep &&
            consecutiveOpenFramesAfterMicrosleep >= microsleepRecoveryFrames
        ) {
            recoveringFromMicrosleep = false
            consecutiveOpenFramesAfterMicrosleep = 0
            suppressCriticalFramesAfterRecovery =
                suppressCriticalFramesAfterRecoveryLimit

            currentFatigueState =
                DriverFatigueState.WARNING
        }

        if (suppressCriticalFramesAfterRecovery > 0) {
            suppressCriticalFramesAfterRecovery -= 1
        }

        val fatigueScore =
            calculateFatigueScore(
                perclos = perclos,
                microsleepActive = microsleepActive,
                isDrowsy = isDrowsy,
                isDistracted = isDistracted,
                isYawning = yawningDetected,
            )

        val state =
            DriverMonitoringState(
                faceDetected = true,
                eyeOpenScore = rawEyeOpenScore,
                smoothedEyeOpenScore = smoothedEyeOpenScore,
                mouthOpenScore = mouthOpenScore,
                isYawning = yawningDetected,
                eyesClosed = effectiveEyesClosed,
                perclos = perclos,
                fatigueScore = fatigueScore,
                fatigueState = currentFatigueState,
                microsleepActive = microsleepActive,
                headYawDeg = yawDeg,
                headPitchDeg = pitchDeg,
                headRollDeg = rollDeg,
                lookingLeft = lookingLeft,
                lookingRight = lookingRight,
                headDown = headDown,
                noFaceFrames = 0,
            )

        DriverMonitoringStateStore.update(
            state,
        )

        speakIfNeeded(
            perclos = perclos,
            microsleepActive = microsleepActive,
            lookingLeft = lookingLeft,
            lookingRight = lookingRight,
            isDistracted = isDistracted,
            headDown = headDown,
            isDrowsy = isDrowsy,
            yawning = yawningDetected,
        )

        Log.d(
            "DriverMonitoring",
            "face=true eye=$rawEyeOpenScore smooth=$smoothedEyeOpenScore threshold=$dynamicClosedThreshold reliable=$eyeMeasurementReliable closed=$effectiveEyesClosed perclos=$perclos state=$currentFatigueState microsleep=$microsleepActive yaw=$yawDeg pitch=$pitchDeg roll=$rollDeg yawn=$yawningDetected"
        )
    }

    private fun handleNoFace() {
        faceMissingFrames += 1
        distractedFrames += 1

        consecutiveClosedFrames = 0
        consecutiveOpenFramesAfterMicrosleep = 0
        microsleepActive = false
        recoveringFromMicrosleep = false

        eyeClosedWindow.addLast(false)

        while (eyeClosedWindow.size > windowSize) {
            eyeClosedWindow.removeFirst()
        }

        val perclos =
            eyeClosedWindow.count { it }.toDouble() /
                    max(
                        eyeClosedWindow.size,
                        1,
                    ).toDouble()

        val noFaceDistracted =
            faceMissingFrames >= faceMissingFramesThreshold

        val previousState =
            currentFatigueState

        currentFatigueState =
            if (noFaceDistracted) {
                DriverFatigueState.DISTRACTED
            } else {
                currentFatigueState
            }

        val fatigueScore =
            calculateFatigueScore(
                perclos = perclos,
                microsleepActive = false,
                isDrowsy = false,
                isDistracted = currentFatigueState == DriverFatigueState.DISTRACTED,
                isYawning = false,
            )

        DriverMonitoringStateStore.update(
            DriverMonitoringState(
                faceDetected = false,
                perclos = perclos,
                fatigueScore = fatigueScore,
                fatigueState = currentFatigueState,
                noFaceFrames = faceMissingFrames,
            )
        )

        if (
            noFaceDistracted &&
            previousState != DriverFatigueState.DISTRACTED
        ) {
            speakAlert(
                key = "face_missing",
                text = ttsText(R.string.dms_tts_face_missing),
                cooldownMs = 8_000L,
            )
        }

        Log.d(
            "DriverMonitoring",
            "face=false noFaceFrames=$faceMissingFrames perclos=$perclos state=$currentFatigueState"
        )
    }

    private fun updateHeadPoseCalibrationIfNeeded(
        yawDeg: Float,
        pitchDeg: Float,
    ) {
        if (
            calibratedYawBaseline != null &&
            calibratedPitchBaseline != null
        ) {
            return
        }

        if (
            abs(yawDeg) <= 10f &&
            abs(pitchDeg) <= 10f
        ) {
            headPoseCalibrationSamples.add(
                yawDeg to pitchDeg,
            )
        }

        if (headPoseCalibrationSamples.size >= 60) {
            calibratedYawBaseline =
                headPoseCalibrationSamples
                    .map { sample ->
                        sample.first
                    }
                    .average()
                    .toFloat()

            calibratedPitchBaseline =
                headPoseCalibrationSamples
                    .map { sample ->
                        sample.second
                    }
                    .average()
                    .toFloat()

            Log.d(
                "DriverMonitoring",
                "calibrated headPose yawBaseline=$calibratedYawBaseline pitchBaseline=$calibratedPitchBaseline",
            )
        }
    }

    private fun updateCalibrationIfNeeded(
        score: Float,
    ) {
        if (calibratedOpenEye != null) {
            return
        }

        if (score > 0.40f) {
            calibrationSamples.add(
                score,
            )
        }

        if (calibrationSamples.size >= 60) {
            val sorted =
                calibrationSamples.sorted()

            val start =
                (sorted.size * 0.6).toInt()

            val upperSamples =
                sorted.subList(
                    start,
                    sorted.size,
                )

            val average =
                upperSamples.average().toFloat()

            calibratedOpenEye =
                max(
                    average,
                    0.55f,
                )

            Log.d(
                "DriverMonitoring",
                "calibrated openEyeBaseline=$calibratedOpenEye"
            )
        }
    }

    private fun measuredFatigueState(
        perclos: Double,
    ): DriverFatigueState {
        if (
            suppressCriticalFramesAfterRecovery > 0 &&
            perclos >= criticalPerclosThreshold
        ) {
            return DriverFatigueState.WARNING
        }
        return when (currentFatigueState) {
            DriverFatigueState.NORMAL,
            DriverFatigueState.DISTRACTED,
            DriverFatigueState.DROWSY -> {
                when {
                    perclos >= criticalPerclosThreshold ->
                        DriverFatigueState.CRITICAL

                    perclos >= warningPerclosThreshold ->
                        DriverFatigueState.WARNING

                    else ->
                        DriverFatigueState.NORMAL
                }
            }

            DriverFatigueState.WARNING -> {
                when {
                    perclos >= criticalPerclosThreshold ->
                        DriverFatigueState.CRITICAL

                    perclos <= normalRecoveryPerclosThreshold ->
                        DriverFatigueState.NORMAL

                    else ->
                        DriverFatigueState.WARNING
                }
            }

            DriverFatigueState.CRITICAL -> {
                if (perclos <= criticalRecoveryPerclosThreshold) {
                    DriverFatigueState.WARNING
                } else {
                    DriverFatigueState.CRITICAL
                }
            }
        }
    }

    private fun updateStateMachine(
        measuredState: DriverFatigueState,
    ): DriverFatigueState {
        if (measuredState == currentFatigueState) {
            candidateState = measuredState
            candidateFrameCount = 0
            return currentFatigueState
        }

        if (measuredState == candidateState) {
            candidateFrameCount += 1
        } else {
            candidateState = measuredState
            candidateFrameCount = 1
        }

        return if (candidateFrameCount >= stateDebounceFrames) {
            candidateFrameCount = 0
            candidateState
        } else {
            currentFatigueState
        }
    }

    private fun calculateFatigueScore(
        perclos: Double,
        microsleepActive: Boolean,
        isDrowsy: Boolean,
        isDistracted: Boolean,
        isYawning: Boolean,
    ): Double {
        var targetScore =
            0.0

        targetScore +=
            min(
                perclos / criticalPerclosThreshold,
                1.0,
            ) * 45.0

        if (microsleepActive) {
            targetScore =
                max(
                    targetScore,
                    95.0,
                )
        }


        if (isDrowsy) {
            targetScore =
                max(
                    targetScore,
                    75.0,
                )
        }

        if (isYawning) {
            targetScore =
                max(
                    targetScore,
                    45.0,
                )
        }

        if (isDistracted) {
            targetScore =
                max(
                    targetScore,
                    30.0,
                )
        }

        if (targetScore >= 60.0) {
            fatigueScoreHoldUntilMs =
                System.currentTimeMillis() + fatigueScoreHoldDurationMs
        }

        if (
            System.currentTimeMillis() < fatigueScoreHoldUntilMs &&
            targetScore < 60.0
        ) {
            targetScore =
                max(
                    targetScore,
                    60.0,
                )
        }

        targetScore =
            min(
                targetScore,
                100.0,
            )


        rawFatigueScore =
            if (targetScore > rawFatigueScore) {
                min(
                    rawFatigueScore + fatigueScoreRiseStep,
                    targetScore,
                )
            } else {
                max(
                    rawFatigueScore - fatigueScoreFallStep,
                    targetScore,
                )
            }

        return min(
            max(
                rawFatigueScore,
                0.0,
            ),
            100.0,
        )
    }

    private fun calculateMouthOpenScore(
        face: Face,
    ): Float {
        val upperLip =
            face.getContour(
                FaceContour.UPPER_LIP_TOP,
            )?.points.orEmpty()

        val lowerLip =
            face.getContour(
                FaceContour.LOWER_LIP_BOTTOM,
            )?.points.orEmpty()

        if (upperLip.isEmpty() || lowerLip.isEmpty()) {
            return 0f
        }

        val upperY =
            upperLip.map { it.y }.average().toFloat()

        val lowerY =
            lowerLip.map { it.y }.average().toFloat()

        val faceHeight =
            face.boundingBox.height().toFloat()

        if (faceHeight <= 0f) {
            return 0f
        }

        return abs(
            lowerY - upperY,
        ) / faceHeight
    }

    private fun speakIfNeeded(
        perclos: Double,
        microsleepActive: Boolean,
        lookingLeft: Boolean,
        lookingRight: Boolean,
        isDistracted: Boolean,
        headDown: Boolean,
        isDrowsy: Boolean,
        yawning: Boolean,
    ) {
        if (microsleepActive) {
            speakAlert(
                key = "microsleep",
                text = ttsText(R.string.dms_tts_microsleep),
                cooldownMs = 8_000L,
            )
            return
        }

        if (perclos >= criticalPerclosThreshold) {
            speakAlert(
                key = "perclos_critical",
                text = ttsText(R.string.dms_tts_perclos_critical),
                cooldownMs = 10_000L,
            )
            return
        }

        if (isDrowsy || headDown) {
            speakAlert(
                key = "head_down",
                text = ttsText(R.string.dms_tts_head_down),
                cooldownMs = 8_000L,
            )
            return
        }

        if (isDistracted && lookingLeft) {
            speakAlert(
                key = "looking_left",
                text = ttsText(R.string.dms_tts_looking_left),
                cooldownMs = 8_000L,
            )
            return
        }

        if (isDistracted && lookingRight) {
            speakAlert(
                key = "looking_right",
                text = ttsText(R.string.dms_tts_looking_right),
                cooldownMs = 8_000L,
            )
            return
        }

        if (yawning) {
            speakAlert(
                key = "yawning",
                text = ttsText(R.string.dms_tts_yawning),
                cooldownMs = 20_000L,
            )
            return
        }

        if (perclos >= warningPerclosThreshold) {
            speakAlert(
                key = "perclos_warning",
                text = ttsText(R.string.dms_tts_perclos_warning),
                cooldownMs = 15_000L,
            )
        }
    }

    private fun speakAlert(
        key: String,
        text: String,
        cooldownMs: Long = voiceAlertCooldownMs,
    ) {
        val now =
            System.currentTimeMillis()

        val lastAt =
            lastVoiceAlertAtByKey[key] ?: 0L

        if (now - lastAt < cooldownMs) {
            return
        }

        lastVoiceAlertAtByKey[key] =
            now

        if (!ttsReady) {
            return
        }

        tts.language =
            currentTtsLocale()

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "driver_monitoring_${key}_${now}",
        )
    }

    fun release() {
        detector.close()
        tts.shutdown()
    }
}