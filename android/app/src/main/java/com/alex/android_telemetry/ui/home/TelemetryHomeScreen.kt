package com.alex.android_telemetry.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alex.android_telemetry.R
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.telemetry.trips.api.DriverHomeResponseDto
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography
import com.alex.android_telemetry.ui.video.DashcamCameraType
import com.alex.android_telemetry.ui.video.DashcamRecordingController
import com.alex.android_telemetry.ui.video.DashcamVideoRepository
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha

import androidx.compose.runtime.DisposableEffect
import com.alex.android_telemetry.telemetry.crash.CrashDetectionManager
import com.alex.android_telemetry.ui.video.DashcamCrashCoordinator
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.alex.android_telemetry.ui.video.CrashClipRepository
import com.alex.android_telemetry.ui.video.CrashClipUploadRepository
import com.alex.android_telemetry.ui.video.CrashClipUploadScheduler
import com.alex.android_telemetry.ui.video.DashcamRecordingService
import androidx.compose.runtime.collectAsState
import com.alex.android_telemetry.ui.video.DashcamRecordingControllerHost
import com.alex.android_telemetry.ui.video.DashcamRecordingStateStore
import com.alex.android_telemetry.ui.video.DashcamTripCoordinatorHolder
import com.alex.android_telemetry.ui.video.DashcamTripOwnership
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import com.alex.android_telemetry.ui.video.CrashClipExactExportScheduler
import com.alex.android_telemetry.ui.video.DriverMonitoringState
import com.alex.android_telemetry.ui.video.DriverMonitoringStateStore
import com.alex.android_telemetry.ui.video.DriverFatigueState
import com.alex.android_telemetry.ui.video.DriverMonitoringMode
import com.alex.android_telemetry.telemetry.crash.CrashTelemetryEventDispatcher


private val HomeScreenBackground = Color.White
private val SwiftSecondarySystemBackground = Color(0xFFF2F2F7)
private val SwiftButtonGray = Color(0xFFE9E9EC)
private val SwiftBlue = Color(0xFF0A84FF)
private val SwiftOrange = Color(0xFFF28C28)
private val SwiftGreen = Color(0xFF34C759)
private val SwiftRed = Color(0xFFFF3B30)
private val SwiftSecondaryText = Color(0xFF8E8E93)


@Composable
fun TelemetryHomeScreen(
    state: TripRuntimeState,
    deviceId: String,
    tripApi: TripApi,
    currentDriverId: String?,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onOpenTripsArchive: () -> Unit,
    onOpenSaveFishGame: () -> Unit,
    onOpenDriverAccount: () -> Unit,
    onOpenPermissionsBackground: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenVideoMode: () -> Unit,
    onOpenVideoArchive: () -> Unit,
    onOpenDriveTelemetryGuide: () -> Unit,
) {
    val isTripActive = state.telemetryMode != TelemetryMode.IDLE
    val hasDriver = !currentDriverId.isNullOrBlank()

    val crashDetectedLabel = stringResource(R.string.home_crash_detected)
    val ratingLoadErrorFallback = stringResource(R.string.home_rating_load_error)
    val selectDriverFirstMessage = stringResource(R.string.home_select_driver_first)

    var homeMetrics by remember { mutableStateOf<DriverHomeResponseDto?>(null) }
    var homeMetricsError by remember { mutableStateOf<String?>(null) }
    var isLoadingHomeMetrics by remember { mutableStateOf(false) }

    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isVideoRecording by rememberSaveable {
        mutableStateOf(false)
    }


    var showCameraPreview by rememberSaveable {
        mutableStateOf(false)
    }

    var useFrontCamera by rememberSaveable {
        mutableStateOf(false)
    }

    var videoRecordingSeconds by rememberSaveable {
        mutableIntStateOf(0)
    }

    val dashcamTripCoordinator =
        remember {
            DashcamTripCoordinatorHolder.instance
        }

    val dashcamTripOwnership by
    dashcamTripCoordinator.ownership.collectAsState()

    val context = LocalContext.current

    var locationWarningDismissed by rememberSaveable {
        mutableStateOf(false)
    }

    var microphoneWarningDismissed by rememberSaveable {
        mutableStateOf(false)
    }

    var cameraWarningDismissed by rememberSaveable {
        mutableStateOf(false)
    }

    var permissionsRefreshKey by remember {
        mutableIntStateOf(0)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsRefreshKey++
    }

    permissionsRefreshKey

    val hasFineLocation =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val hasCoarseLocation =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val hasLocationPermission =
        hasFineLocation || hasCoarseLocation

    val hasCameraPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    val hasMicrophonePermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    val openAppSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        )
    }

    val dashcamRepository =
        remember {
            DashcamVideoRepository(context)
        }

    val dashcamController =
        remember {
            DashcamRecordingControllerHost.get(context)
        }

    val dashcamRecordingState by
    DashcamRecordingStateStore.state.collectAsState()

    val driverMonitoringState by
    DriverMonitoringStateStore.state.collectAsState()

    val homeScrollState =
        rememberScrollState()


    val isVideoSaving = dashcamRecordingState.isSaving
    val videoSavingProgressPercent = dashcamRecordingState.savingProgressPercent

    LaunchedEffect(dashcamRecordingState.isRecording) {
        val wasRecording =
            isVideoRecording

        isVideoRecording =
            dashcamRecordingState.isRecording

        if (!wasRecording && dashcamRecordingState.isRecording) {
            delay(300)

            homeScrollState.animateScrollTo(
                homeScrollState.maxValue,
            )
        }
    }

    LaunchedEffect(showCameraPreview) {
        if (showCameraPreview) {
            delay(300)

            homeScrollState.animateScrollTo(
                homeScrollState.maxValue,
            )
        }
    }
    LaunchedEffect(
        isTripActive,
        isVideoRecording,
    ) {
        dashcamTripCoordinator.syncRuntimeState(
            isTripActive = isTripActive,
            isVideoRecording = isVideoRecording,
        )
    }


    val crashDetectionManager =
        remember {
            CrashDetectionManager(context)
        }

    val crashClipRepository =
        remember {
            CrashClipRepository(
                context = context,
                videoRepository = dashcamRepository,
            )
        }

    val crashUploadScheduler =
        remember {
            CrashClipUploadScheduler(context)
        }

    LaunchedEffect(Unit) {
        crashClipRepository
            .pendingUploads()
            .forEach { crashClip ->

                val driverId =
                    currentDriverId?.trim().orEmpty()

                if (driverId.isBlank()) {
                    return@forEach
                }

                val cameraType =
                    runCatching {
                        DashcamCameraType.valueOf(
                            crashClip
                                .segmentPaths
                                .firstOrNull()
                                ?.substringBefore('_')
                                ?.uppercase()
                                ?: "ROAD"
                        )
                    }.getOrDefault(
                        DashcamCameraType.ROAD
                    )

                crashUploadScheduler.enqueueUpload(
                    crashId = crashClip.crashId,
                    driverId = driverId,
                    deviceId = deviceId,
                    cameraType = cameraType,
                )
            }
    }

    val scope =
        rememberCoroutineScope()

    val dashcamCrashCoordinator =
        remember(
            deviceId,
            currentDriverId,
            state.sessionId,
        ) {
            DashcamCrashCoordinator(
                recordingController = dashcamController,
                crashClipRepository = crashClipRepository,
                exactExportScheduler = CrashClipExactExportScheduler(context),
                crashTelemetryEventDispatcher = CrashTelemetryEventDispatcher(context),
                scope = scope,
                deviceId = deviceId,
                driverIdProvider = {
                    currentDriverId
                },
                tripSessionIdProvider = {
                    state.sessionId
                },
            )
        }
    var crashAlertVisible by remember {
        mutableStateOf(false)
    }

    var crashAlertText by remember {
        mutableStateOf("")
    }


    DisposableEffect(
        isTripActive,
        isVideoRecording,
    ) {
        if (isTripActive || isVideoRecording) {
            crashDetectionManager.start { crashEvent ->

                dashcamCrashCoordinator.handleCrashDetected(crashEvent)

                crashAlertText =
                    crashDetectedLabel + " • ${"%.2f".format(crashEvent.gForce)}g"

                crashAlertVisible = true

                scope.launch {
                    kotlinx.coroutines.delay(20_000L)
                    crashAlertVisible = false
                }
            }
        }

        onDispose {
            crashDetectionManager.stop()
        }
    }



    LaunchedEffect(isTripActive, state.startedAt) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    LaunchedEffect(isVideoRecording) {

        while (isVideoRecording) {

            kotlinx.coroutines.delay(1000)

            videoRecordingSeconds++
        }
    }

    LaunchedEffect(deviceId, currentDriverId) {
        if (deviceId.isBlank() || currentDriverId.isNullOrBlank()) {
            homeMetrics = null
            homeMetricsError = null
            isLoadingHomeMetrics = false
            return@LaunchedEffect
        }

        isLoadingHomeMetrics = true
        homeMetricsError = null

        runCatching {
            tripApi.fetchDriverHome(
                deviceId = deviceId,
                driverId = currentDriverId,
            )
        }.onSuccess { response ->
            homeMetrics = response
            homeMetricsError = null
        }.onFailure { throwable ->
            homeMetrics = null
            homeMetricsError = throwable.message ?: ratingLoadErrorFallback
        }

        isLoadingHomeMetrics = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeScreenBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(homeScrollState)
                .padding(horizontal = 10.dp)
                .padding(top = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HomeToolbar(
                onOpenSettings = onOpenPermissionsBackground,
            )

            if (!hasLocationPermission && !locationWarningDismissed) {
                PermissionWarningCard(
                    title = stringResource(R.string.home_location_disabled_title),
                    message = stringResource(R.string.home_location_disabled_message),
                    onOpenSettings = openAppSettings,
                    onDismiss = {
                        locationWarningDismissed = true
                    },
                )
            }

            if (!hasCameraPermission && !cameraWarningDismissed) {
                PermissionWarningCard(
                    title = stringResource(R.string.home_camera_permission_title),
                    message = stringResource(R.string.home_camera_permission_message),
                    onOpenSettings = openAppSettings,
                    onDismiss = {
                        cameraWarningDismissed = true
                    },
                )
            }

            if (!hasMicrophonePermission && !microphoneWarningDismissed) {
                PermissionWarningCard(
                    title = stringResource(R.string.home_microphone_permission_title),
                    message = stringResource(R.string.home_microphone_permission_message),
                    onOpenSettings = openAppSettings,
                    onDismiss = {
                        microphoneWarningDismissed = true
                    },
                )
            }


            DriverScoreCard(
                metrics = homeMetrics,
                isLoading = isLoadingHomeMetrics,
                errorText = homeMetricsError,
                currentDriverId = currentDriverId,
                onTripsTap = onOpenTripsArchive,
            )

            TripStateBadge(
                isTripActive = isTripActive,
            )

            if (isTripActive) {
                TripSummaryCard(
                    state = state,
                    nowEpochMs = nowEpochMs,
                )
            }

            StartStopControls(
                canStart =
                    !isTripActive ||
                            dashcamTripOwnership == DashcamTripOwnership.VIDEO_IMPLICIT,
                canStop = isTripActive,
                hasDriver = hasDriver,
                onStartTrip = {
                    if (!hasDriver) {
                        android.widget.Toast
                            .makeText(
                                context,
                                selectDriverFirstMessage,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            .show()
                        return@StartStopControls
                    }



                    dashcamTripCoordinator.handleManualTripStart(
                        startTrip = onStartTrip,
                        stopTrip = onStopTrip,
                    )
                },
                onStopTrip = {
                    dashcamTripCoordinator.handleManualTripStop(
                        isVideoRecording = isVideoRecording,
                        startTrip = onStartTrip,
                        stopTrip = onStopTrip,
                    )
                },
            )

            ArchiveActions(
                onOpenTripsArchive = onOpenTripsArchive,
                onOpenVideoArchive = onOpenVideoArchive,
            )

            DriveTelemetryGuideButton(
                onClick = onOpenDriveTelemetryGuide,
            )

            DashcamBlock(
                isRecording = isVideoRecording,
                isSaving = isVideoSaving,
                savingProgressPercent = videoSavingProgressPercent,
                recordingSeconds = videoRecordingSeconds,
                showPreview = showCameraPreview,
                useFrontCamera = useFrontCamera,
                driverMonitoringState = driverMonitoringState,
                isEmergency = dashcamRecordingState.isEmergency,

                dashcamController = dashcamController,
                onToggleRecording = {
                    if (isVideoRecording) {
                        DashcamRecordingService.stop(context)

                        dashcamTripCoordinator.handleVideoStop(
                            stopTrip = onStopTrip,
                        )

                        isVideoRecording = false
                        videoRecordingSeconds = 0
                    } else {
                        if (!hasDriver) {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    selectDriverFirstMessage,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                .show()
                            return@DashcamBlock
                        }



                        dashcamTripCoordinator.handleVideoStart(
                            isTripActive = isTripActive,
                            startTrip = onStartTrip,
                        )

                        videoRecordingSeconds = 0

                        DashcamRecordingService.start(
                            context = context,
                            cameraType =
                                if (useFrontCamera) {
                                    DashcamCameraType.DRIVER
                                } else {
                                    DashcamCameraType.ROAD
                                },
                            driverId = currentDriverId,
                            deviceId = deviceId,
                            tripSessionId = state.sessionId,
                        )
                    }
                },
                onTogglePreview = {
                    showCameraPreview = !showCameraPreview
                },
                onSelectRoadCamera = {
                    useFrontCamera = false
                },
                onSelectDriverCamera = {
                    useFrontCamera = true
                },

            )

            TrackingModeSegment()





            SaveFishButton(
                onClick = onOpenSaveFishGame,
            )

            Spacer(Modifier.height(24.dp))

//            TextButton(
//                onClick = onOpenDiagnostics,
//                modifier = Modifier.fillMaxWidth(),
//            ) {
//                Text(
//                    text = stringResource(R.string.home_diagnostics),
//                    color = SwiftSecondaryText,
//                    style = TelemetryTypography.Callout,
//                )
//            }
        }
        AnimatedVisibility(
            visible = crashAlertVisible,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFF453A))
                        .padding(
                            horizontal = 10.dp,
                            vertical = 8.dp,
                        ),
            ) {
                Text(
                    text = crashAlertText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    title: String,
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFFFF4D6))
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFD60A),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(14.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = 34.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "⚠ $title",
                color = Color(0xFF5C4300),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = message,
                color = Color(0xFF5C4300),
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )

            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = stringResource(R.string.home_open_settings),
                    color = SwiftBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        TextButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                text = "×",
                color = Color(0xFF5C4300),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
@Composable
private fun HomeToolbar(
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0x11000000),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.home_settings),
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun DriverScoreCard(
    metrics: DriverHomeResponseDto?,
    isLoading: Boolean,
    errorText: String?,
    currentDriverId: String?,
    onTripsTap: () -> Unit,
) {
    val scoreText = when {
        currentDriverId.isNullOrBlank() -> "— / 100"
        isLoading -> "…"
        metrics?.avgScore != null -> "${metrics.avgScore.roundToInt()} / 100"
        else -> "— / 100"
    }

    val primarySubtitle = when {
        currentDriverId.isNullOrBlank() -> stringResource(R.string.home_driver_not_selected)
        isLoading -> stringResource(R.string.home_rating_loading)
        errorText != null -> stringResource(R.string.home_rating_unavailable)
        metrics?.driverLevel != null -> localizedDriverLevel(metrics.driverLevel)
        metrics?.ratingStatus == "forming" -> stringResource(R.string.home_rating_forming)
        metrics != null -> stringResource(R.string.home_rating_ready)
        else -> stringResource(R.string.home_rating_after_trips)
    }

    val ratingFormingText = if (metrics?.ratingStatus == "forming") {
        stringResource(R.string.home_rating_forming_trips_left, metrics.tripsToUnlockPercentile)
    } else {
        null
    }

    val deltaText = formatScoreDelta(metrics?.scoreDeltaRecent)
    val percentileText = formatPercentile(metrics)
    val nextLevelText = formatLevelText(metrics)

    SwiftCard(
        cornerRadius = 22,
        padding = 6,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_driver_score_title),
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 27.sp,
                textAlign = TextAlign.Center,
            )

            Text(
                text = scoreText,
                color = SwiftBlue,
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 54.sp,
                textAlign = TextAlign.Center,
            )

            Text(
                text = primarySubtitle,
                color = SwiftSecondaryText,
                style = TelemetryTypography.Body,
                textAlign = TextAlign.Center,
            )

            if (!currentDriverId.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.home_driver_value, currentDriverId),
                    color = SwiftSecondaryText,
                    style = TelemetryTypography.Body,
                    textAlign = TextAlign.Center,
                )
            }

            if (deltaText != null) {
                Text(
                    text = deltaText,
                    color = if ((metrics?.scoreDeltaRecent ?: 0.0) >= 0.0) SwiftGreen else SwiftOrange,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,

                )

                SwiftDivider()
            }

            if (ratingFormingText != null) {
                Text(
                    text = ratingFormingText,
                    color = SwiftSecondaryText,
                    style = TelemetryTypography.Body,
                    textAlign = TextAlign.Center,
                )
            } else if (percentileText != null) {
                Text(
                    text = percentileText,
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }

            if (nextLevelText != null) {
                Text(
                    text = nextLevelText,
                    color = SwiftSecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            if (metrics?.recentTripScores?.isNotEmpty() == true) {
                SwiftDivider()

                TextButton(
                    onClick = onTripsTap,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        RecentTripDots(
                            scores = metrics.recentTripScores,
                            colors = metrics.recentTripColors,
                        )

                        Text(
                            text = recentTripsSummary(metrics.recentTripScores),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,

                        )

                        Text(
                            text = stringResource(R.string.home_keep_green_streak),
                            color = SwiftSecondaryText,
                            fontSize = 12.sp,
                            style = TelemetryTypography.Body,
                            textAlign = TextAlign.Center,

                        )
                    }
                }
            }

            if (errorText != null) {
                Text(
                    text = stringResource(R.string.home_rating_data_unavailable),
                    color = SwiftSecondaryText,
                    style = TelemetryTypography.Caption,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TripStateBadge(
    isTripActive: Boolean,
) {
    val dotColor by animateColorAsState(
        label = "tripStateDot",
        targetValue = if (isTripActive) SwiftRed else SwiftGreen,
    )

    SwiftCard(
        cornerRadius = 18,
        padding = 2,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )

            Text(
                text = if (isTripActive) stringResource(R.string.home_trip_recording) else stringResource(R.string.home_trip_ready),
                color = Color.Black,
                style = TelemetryTypography.Title2,
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TripSummaryCard(
    state: TripRuntimeState,
    nowEpochMs: Long,
) {
    val elapsedSec = elapsedSeconds(
        startedAt = state.startedAt,
        nowEpochMs = nowEpochMs,
    )

    val distanceKm = state.distanceM / 1000.0

    SwiftCard(
        cornerRadius = 16,
        padding = 2,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TripSummaryRow(
                icon = "◴",
                label = stringResource(R.string.home_current_speed),
                value = "${formatOneDecimal((state.currentSpeedMS ?: 0.0) * 3.6)} km/h",
            )

            TripSummaryRow(
                icon = "↻",
                label = stringResource(R.string.home_trip_time),
                value = formatElapsed(elapsedSec),
            )

            TripSummaryRow(
                icon = "▤",
                label = stringResource(R.string.home_distance),
                value = stringResource(R.string.home_distance_km_value, formatTwoDecimals(distanceKm)),
            )
        }
    }
}

@Composable
private fun TripSummaryRow(
    icon: String,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            modifier = Modifier.width(28.dp),
            color = SwiftSecondaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = SwiftSecondaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 23.sp,
            maxLines = 1,
        )

        Text(
            text = value,
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
@Composable
private fun StartStopControls(
    canStart: Boolean,
    canStop: Boolean,
    hasDriver: Boolean,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onStartTrip,
            enabled = true,
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (canStart && hasDriver) {
                        SwiftBlue
                    } else {
                        SwiftButtonGray
                    },
                disabledContainerColor = SwiftButtonGray,
                contentColor =
                    if (canStart && hasDriver) {
                        Color.White
                    } else {
                        Color(0xFFB8B8BE)
                    },
                disabledContentColor = Color(0xFFB8B8BE),
            ),
        ) {
            Text(
                text = stringResource(R.string.home_start),
                style = TelemetryTypography.Title2,
            )
        }

        Button(
            onClick = onStopTrip,
            enabled = canStop,
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SwiftButtonGray,
                disabledContainerColor = SwiftButtonGray,
                contentColor = SwiftBlue,
                disabledContentColor = Color(0xFFB8B8BE),
            ),
        ) {
            Text(
                text = stringResource(R.string.home_stop),
                style = TelemetryTypography.Title2,
            )
        }
    }
}


@Composable
private fun ArchiveActions(
    onOpenTripsArchive: () -> Unit,
    onOpenVideoArchive: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        PillButton(
            text = stringResource(R.string.home_trips_archive),
            modifier = Modifier.weight(1f),
            height = 50,
            onClick = onOpenTripsArchive,
        )

        PillButton(
            text = stringResource(R.string.home_video_archive),
            modifier = Modifier.weight(1f),
            height = 50,
            onClick = onOpenVideoArchive,
        )
    }
}

@Composable
private fun VideoModeButton(
    onOpenVideoMode: () -> Unit,
) {
    PillButton(
        text = stringResource(R.string.home_video_mode),
        modifier = Modifier.fillMaxWidth(),
        height = 64,
        onClick = onOpenVideoMode,
    )
}

@Composable
private fun TrackingModeSegment() {

}
@Composable
private fun DriveTelemetryGuideButton(
    onClick: () -> Unit,
) {
    PillButton(
        text = "🚗 Возможности DriveTelemetry",
        modifier = Modifier.fillMaxWidth(),
        height = 58,
        backgroundColor = Color(0xFFE8F2FF),
        contentColor = SwiftBlue,
        onClick = onClick,
    )
}


@Composable
private fun SaveFishButton(
    onClick: () -> Unit,
) {
    PillButton(
        text = stringResource(R.string.home_save_fish_game),
        modifier = Modifier.fillMaxWidth(),
        height = 64,
        onClick = onClick,
    )
}

@Composable
private fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Int = 58,
    backgroundColor: Color = SwiftButtonGray,
    contentColor: Color = SwiftBlue,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height.dp),
        shape = RoundedCornerShape((height / 2).dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 6.dp,
        ),
    ) {
        Text(
            text = text,
            style = TelemetryTypography.BodyEmphasis,
            textAlign = TextAlign.Center,
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
        )
    }
}
@Composable
private fun SwiftCard(
    cornerRadius: Int,
    padding: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(SwiftSecondarySystemBackground)
            .border(
                width = 1.dp,
                color = TelemetrySwiftColors.Divider.copy(alpha = 0.25f),
                shape = RoundedCornerShape(cornerRadius.dp),
            )
            .padding(padding.dp),
    ) {
        content()
    }
}

@Composable
private fun SwiftDivider() {
    Divider(
        modifier = Modifier.padding(vertical = 3.dp),
        color = TelemetrySwiftColors.Divider,
    )
}

@Composable
private fun RecentTripDots(
    scores: List<Double>,
    colors: List<String>,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        scores.takeLast(5).forEachIndexed { index, score ->
            val colorName = colors.getOrNull(index)
            RecentTripDot(
                color = recentTripColor(score = score, colorName = colorName),
                size = if (index == 0) 18 else 14,
            )
        }
    }
}

@Composable
private fun RecentTripDot(
    color: Color,
    size: Int,
) {
    Box(
        modifier = Modifier
            .width(size.dp)
            .height(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun DashcamBlock(
    isRecording: Boolean,
    isSaving: Boolean,
    isEmergency: Boolean,
    savingProgressPercent: Int,
    recordingSeconds: Int,
    showPreview: Boolean,
    useFrontCamera: Boolean,
    driverMonitoringState: DriverMonitoringState,

    dashcamController: DashcamRecordingController,
    onToggleRecording: () -> Unit,
    onTogglePreview: () -> Unit,
    onSelectRoadCamera: () -> Unit,
    onSelectDriverCamera: () -> Unit,

) {
    val context = LocalContext.current
    val cameraSwitchRequiresNewRecordingMessage =
        stringResource(R.string.home_camera_switch_requires_new_recording)

    var permissionsRefreshKey by remember {
        mutableIntStateOf(0)
    }

    var locationWarningDismissed by rememberSaveable {
        mutableStateOf(false)
    }

    var microphoneWarningDismissed by rememberSaveable {
        mutableStateOf(false)
    }

    var cameraWarningDismissed by rememberSaveable {
        mutableStateOf(false)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsRefreshKey++
    }

    val hasFineLocation =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val hasCoarseLocation =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val hasLocationPermission =
        hasFineLocation || hasCoarseLocation

    val hasCameraPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    val hasMicrophonePermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    val openAppSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        PillButton(
            text = stringResource(R.string.home_video_mode),
            modifier = Modifier.fillMaxWidth(),
            height = 50,
            backgroundColor =
                if (isSaving) {
                    Color(0xFFE5E5EA)
                } else {
                    SwiftButtonGray
                },
            contentColor =
                if (isSaving) {
                    Color(0xFFB8B8BE)
                } else {
                    SwiftBlue
                },
            onClick = {
                if (!isRecording && !isSaving) {
                    onToggleRecording()
                }
            },
        )

//        if (isEmergency) {
//            Text(
//                text = stringResource(R.string.dashcam_crash_saving),
//                color = Color(0xFFFF4D4F),
//                fontSize = 16.sp,
//                fontWeight = FontWeight.Bold,
//                modifier = Modifier.padding(bottom = 8.dp),
//            )
//        }

        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF4D4F))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stringResource(R.string.home_video_recording),
                        color = Color.Black,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 27.sp,
                    )
                }

                Text(
                    text = formatDashcamTime(recordingSeconds),
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PillButton(
                    text =
                        if (showPreview) {
                            stringResource(R.string.home_hide_camera)
                        } else {
                            stringResource(R.string.home_show_camera)
                        },
                    modifier = Modifier.weight(1f),
                    height = 56,
                    onClick = onTogglePreview,
                )

                PillButton(
                    text = stringResource(R.string.home_stop_video),
                    modifier = Modifier.weight(1f),
                    height = 56,
                    backgroundColor = Color(0xFFFF4D4F),
                    contentColor = Color.White,
                    onClick = onToggleRecording,
                )
            }

            InlineCameraPreview(
                useFrontCamera = useFrontCamera,
                dashcamController = dashcamController,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(
                        if (showPreview) {
                            240.dp
                        } else {
                            1.dp
                        }
                    )
                    .alpha(
                        if (showPreview) {
                            1f
                        } else {
                            0f
                        }
                    )
                    .clip(RoundedCornerShape(28.dp))
            )

            if (useFrontCamera) {
                DriverFatigueCard(
                    state = driverMonitoringState,
                    isRecording = isRecording,
                )
            }
        }

        if (isSaving) {
            SavingVideoProgressBlock(
                progressPercent = savingProgressPercent,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF1F1F3))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CameraSegmentButton(
                text = stringResource(R.string.home_road_camera),
                selected = !useFrontCamera,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (!isRecording) {
                        onSelectRoadCamera()
                    } else {
                        android.widget.Toast
                            .makeText(
                                context,
                                cameraSwitchRequiresNewRecordingMessage,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                },
            )

            CameraSegmentButton(
                text = stringResource(R.string.home_driver_camera),
                selected = useFrontCamera,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (!isRecording) {
                        onSelectDriverCamera()
                    } else {
                        android.widget.Toast
                            .makeText(
                                context,
                                cameraSwitchRequiresNewRecordingMessage,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                },
            )
        }



        if (!hasLocationPermission && !locationWarningDismissed) {
            PermissionWarningCard(
                title = stringResource(R.string.home_location_disabled_title),
                message = stringResource(R.string.home_location_disabled_message),
                onOpenSettings = openAppSettings,
                onDismiss = {
                    locationWarningDismissed = true
                },
            )
        }

        if (!hasCameraPermission && !cameraWarningDismissed) {
            PermissionWarningCard(
                title = stringResource(R.string.home_camera_permission_title),
                message = stringResource(R.string.home_camera_permission_message),
                onOpenSettings = openAppSettings,
                onDismiss = {
                    cameraWarningDismissed = true
                },
            )
        }

        if (!hasMicrophonePermission && !microphoneWarningDismissed) {
            PermissionWarningCard(
                title = stringResource(R.string.home_microphone_permission_title),
                message = stringResource(R.string.home_microphone_permission_message),
                onOpenSettings = openAppSettings,
                onDismiss = {
                    microphoneWarningDismissed = true
                },
            )
        }
    }
}

@Composable
private fun SavingVideoProgressBlock(
    progressPercent: Int,
) {
    val safeProgress =
        progressPercent.coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9500)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.home_video_saving_title),
                modifier = Modifier.weight(1f),
                color = Color.Black,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 25.sp,
            )

            Text(
                text = stringResource(
                    R.string.home_video_saving_percent,
                    safeProgress,
                ),
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
            )
        }

        LinearProgressIndicator(
            progress = safeProgress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = SwiftBlue,
            trackColor = Color(0xFFE5E5EA),
        )

        Text(
            text = stringResource(R.string.home_video_saving_subtitle),
            modifier = Modifier.fillMaxWidth(),
            color = SwiftSecondaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
@Composable
private fun CameraSegmentButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (selected) {
                    Color.White
                } else {
                    Color.Transparent
                },
            contentColor =
                if (selected) {
                    Color.Black
                } else {
                    Color.Gray
                },
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 0.dp,
        ),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
@Composable
private fun InlineCameraPreview(
    useFrontCamera: Boolean,
    dashcamController: DashcamRecordingController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasCameraPermission = granted
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_camera_permission_required),
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val intent =
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts(
                                    "package",
                                    context.packageName,
                                    null,
                                ),
                            )

                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.home_open_settings))
                }
            }
        }
        return
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    MATCH_PARENT,
                    MATCH_PARENT,
                )

                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val providerFuture =
                ProcessCameraProvider.getInstance(context)

            providerFuture.addListener(
                {
                    val provider =
                        providerFuture.get()

                    dashcamController.bindPreview(
                        lifecycleOwner = lifecycleOwner,
                        provider = provider,
                        preview = null,
                        cameraType =
                            if (useFrontCamera) {
                                DashcamCameraType.DRIVER
                            } else {
                                DashcamCameraType.ROAD
                            },
                    )

                    dashcamController.attachPreviewSurface(
                        previewView.surfaceProvider,
                    )
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            dashcamController.detachPreviewSurface()
        }
    }
}

private fun formatDashcamTime(seconds: Int): String {

    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60

    return String.format(
        java.util.Locale.US,
        "%02d:%02d:%02d",
        hrs,
        mins,
        secs,
    )
}

@Composable
private fun formatScoreDelta(value: Double?): String? {
    if (value == null) return null

    val rounded = formatOneDecimal(abs(value))
    return when {
        value > 0.0 -> stringResource(R.string.home_score_delta_up, rounded)
        value < 0.0 -> stringResource(R.string.home_score_delta_down, rounded)
        else -> stringResource(R.string.home_score_delta_same)
    }
}

@Composable
private fun formatPercentile(metrics: DriverHomeResponseDto?): String? {
    if (metrics == null) return null

    val pct = metrics.betterThanDriversPct
    if (pct != null) {
        val roundedPct = pct.roundToInt()

        return if (roundedPct <= 50) {
            stringResource(R.string.home_better_than_drivers, roundedPct)
        } else {
            stringResource(R.string.home_top_drivers, maxOf(1, 100 - roundedPct))
        }
    }

    val rank = metrics.driverRank
    val total = metrics.totalDrivers
    if (rank != null && total > 0) {
        return stringResource(R.string.home_driver_rank, rank, total)
    }

    return null
}

@Composable
private fun formatLevelText(metrics: DriverHomeResponseDto?): String? {
    if (metrics == null) return null

    val nextLevel = metrics.nextLevel
    val points = metrics.pointsToNextLevel

    return when {
        nextLevel != null && points != null -> {
            stringResource(
                R.string.home_points_to_next_level,
                localizedDriverLevel(nextLevel),
                formatOneDecimal(points),
            )
        }
        metrics.driverLevel != null -> {
            localizedDriverLevel(metrics.driverLevel)
        }
        else -> null
    }
}

@Composable
private fun recentTripsSummary(scores: List<Double>): String {
    if (scores.isEmpty()) return stringResource(R.string.home_recent_trips_empty)

    val last = scores.last()
    val best = scores.maxOrNull()

    return if (best != null && last >= best) {
        stringResource(R.string.home_last_trip_best)
    } else {
        stringResource(R.string.home_trip_can_improve)
    }
}

private fun recentTripColor(
    score: Double,
    colorName: String?,
): Color {
    return when (colorName?.lowercase()) {
        "green" -> SwiftGreen
        "red" -> SwiftRed
        "orange", "yellow" -> SwiftOrange
        else -> {
            if (score >= 80.0) {
                SwiftGreen
            } else if (score >= 60.0) {
                SwiftOrange
            } else {
                SwiftRed
            }
        }
    }
}

@Composable
private fun localizedDriverLevel(raw: String): String {
    return when (raw.trim().lowercase()) {
        "risky_driver", "risky driver", "risky" -> stringResource(R.string.home_driver_level_risky)
        "average_driver", "average driver", "average" -> stringResource(R.string.home_driver_level_average)
        "calm_driver", "calm driver", "calm" -> stringResource(R.string.home_driver_level_calm)
        "safe_driver", "safe driver", "safe" -> stringResource(R.string.home_driver_level_safe)
        else -> raw
    }
}

@Composable
private fun DriverFatigueCard(
    state: DriverMonitoringState,
    isRecording: Boolean,
) {
    val statusText =
        if (isRecording && state.monitoringMode == DriverMonitoringMode.OFF) {
            stringResource(R.string.home_dms_status_full)
        } else {
            when (state.monitoringMode) {
                DriverMonitoringMode.OFF ->
                    stringResource(R.string.home_dms_status_off)

                DriverMonitoringMode.SAFE ->
                    stringResource(R.string.home_dms_status_safe)

                DriverMonitoringMode.FULL ->
                    stringResource(R.string.home_dms_status_full)
            }
        }

    val fatigueText =
        when (state.fatigueState) {
            DriverFatigueState.NORMAL ->
                stringResource(R.string.home_dms_fatigue_normal)

            DriverFatigueState.WARNING ->
                stringResource(R.string.home_dms_fatigue_warning)

            DriverFatigueState.CRITICAL ->
                stringResource(R.string.home_dms_fatigue_critical)

            DriverFatigueState.DISTRACTED ->
                stringResource(R.string.home_dms_fatigue_distracted)

            DriverFatigueState.DROWSY ->
                stringResource(R.string.home_dms_fatigue_drowsy)
        }

    val alertColor =
        when (state.fatigueState) {
            DriverFatigueState.CRITICAL ->
                SwiftRed

            DriverFatigueState.WARNING,
            DriverFatigueState.DISTRACTED,
            DriverFatigueState.DROWSY ->
                SwiftOrange

            DriverFatigueState.NORMAL ->
                SwiftGreen
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF2F2F7))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.home_dms_title),
            color = Color.Black,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = statusText,
            color = SwiftSecondaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        Text(
            text = fatigueText,
            color = alertColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(
                R.string.home_dms_eye_score,
                (state.smoothedEyeOpenScore * 100f).roundToInt(),
            ),
            color = Color.Black,
            fontSize = 15.sp,
        )

        Text(
            text = stringResource(
                R.string.home_dms_perclos,
                (state.perclos * 100.0).roundToInt(),
            ),
            color = Color.Black,
            fontSize = 15.sp,
        )

        if (state.microsleepActive) {
            Text(
                text = stringResource(R.string.home_dms_microsleep),
                color = SwiftRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!state.faceDetected && state.monitoringMode != DriverMonitoringMode.OFF) {
            Text(
                text = stringResource(R.string.home_dms_face_not_detected),
                color = SwiftOrange,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun elapsedSeconds(
    startedAt: kotlinx.datetime.Instant?,
    nowEpochMs: Long,
): Long {
    if (startedAt == null) return 0L

    val startedEpochMs = startedAt.toEpochMilliseconds()
    return maxOf(0L, (nowEpochMs - startedEpochMs) / 1000L)
}

private fun formatElapsed(seconds: Long): String {
    val safe = maxOf(0L, seconds)
    val minutes = safe / 60
    val sec = safe % 60
    return "%02d:%02d".format(minutes, sec)
}

private fun formatOneDecimal(value: Double): String {
    return "%.1f".format(value)
}

private fun formatTwoDecimals(value: Double): String {
    return "%.2f".format(value)
}