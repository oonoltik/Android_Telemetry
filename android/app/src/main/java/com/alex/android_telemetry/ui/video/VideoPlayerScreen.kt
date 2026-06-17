package com.alex.android_telemetry.ui.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.alex.android_telemetry.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoPath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val videoRepository =
        remember {
            DashcamVideoRepository(context)
        }

    val crashRepository =
        remember {
            CrashClipRepository(
                context = context,
                videoRepository = videoRepository,
            )
        }

    val crashClip =
        remember(videoPath) {
            crashRepository
                .loadCrashClips()
                .firstOrNull {
                    it.mergedClipPath == videoPath ||
                            it.segmentPaths.contains(videoPath)
                }
        }

    val segment =
        remember(videoPath) {
            videoRepository
                .loadVideos()
                .firstOrNull {
                    it.absolutePath == videoPath
                }
        }

    val player =
        remember(videoPath) {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    setMediaItem(
                        MediaItem.fromUri(
                            Uri.fromFile(File(videoPath)),
                        )
                    )
                    prepare()
                    playWhenReady = true
                }
        }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        PlayerHeader(
            isCrashClip = crashClip != null,
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black,
            ),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Color.Black),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (crashClip != null) {
            CrashPlayerMetadataCard(
                item = crashClip,
            )

            Spacer(modifier = Modifier.height(14.dp))

            CrashPlayerTimelineCard(
                item = crashClip,
            )
        } else if (segment != null) {
            SegmentPlayerMetadataCard(
                item = segment,
            )
        } else {
            UnknownVideoMetadataCard(
                videoPath = videoPath,
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    isCrashClip: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1E),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(18.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(stringResource(R.string.video_player_back))
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text =
                if (isCrashClip) {
                    stringResource(R.string.video_player_emergency_clip)
                } else {
                    stringResource(R.string.video_player_dashcam_playback)
                },
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CrashPlayerMetadataCard(
    item: CrashClipEntity,
) {
    val snapshot =
        item.telemetrySnapshot

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111318),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArchiveBadge(
                    text = stringResource(R.string.video_player_badge_emergency),
                    color = Color(0xFFFF453A),
                )

                Spacer(modifier = Modifier.width(8.dp))

                ArchiveBadge(
                    text = stringResource(R.string.video_player_badge_protected),
                    color = Color(0xFFFF9F0A),
                )

                Spacer(modifier = Modifier.width(8.dp))

                ArchiveBadge(
                    text =
                        when (item.uploadState) {
                            CrashClipUploadState.UPLOADED -> stringResource(R.string.video_player_upload_synced)
                            CrashClipUploadState.UPLOADING -> stringResource(R.string.video_player_upload_syncing)
                            CrashClipUploadState.QUEUED -> stringResource(R.string.video_player_upload_queued)
                            CrashClipUploadState.FAILED -> stringResource(R.string.video_player_upload_failed)
                            CrashClipUploadState.LOCAL_ONLY -> stringResource(R.string.video_player_upload_local)
                        },
                    color =
                        when (item.uploadState) {
                            CrashClipUploadState.UPLOADED -> Color(0xFF30D158)
                            CrashClipUploadState.UPLOADING -> Color(0xFF64D2FF)
                            CrashClipUploadState.QUEUED -> Color(0xFFFF9F0A)
                            CrashClipUploadState.FAILED -> Color(0xFFFF453A)
                            CrashClipUploadState.LOCAL_ONLY -> Color(0xFF8E8E93)
                        },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.video_player_crash_details),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            MetadataLine(
                label = stringResource(R.string.video_player_detected),
                value = formatVideoDate(item.detectedAtMs),
            )

            MetadataLine(
                label = stringResource(R.string.video_player_impact),
                value = "${formatGForce(item.gForce)}g",
            )

            MetadataLine(
                label = stringResource(R.string.video_player_window),
                value = "-${item.preCrashMs / 1000}s / +${item.postCrashMs / 1000}s",
            )

            MetadataLine(
                label = stringResource(R.string.video_player_segments),
                value = item.segmentPaths.size.toString(),
            )

            if (snapshot != null) {
                MetadataLine(
                    label = stringResource(R.string.video_player_speed),
                    value =
                        snapshot.speedKmh?.let {
                            "${it.toInt()} km/h"
                        } ?: stringResource(R.string.common_not_available),
                )

                MetadataLine(
                    label = stringResource(R.string.video_player_heading),
                    value =
                        snapshot.headingDeg?.let {
                            "${it.toInt()}°"
                        } ?: stringResource(R.string.common_not_available),
                )

                MetadataLine(
                    label = stringResource(R.string.video_player_gps),
                    value =
                        if (snapshot.lat != null && snapshot.lon != null) {
                            String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                snapshot.lat,
                                snapshot.lon,
                            )
                        } else {
                            stringResource(R.string.common_not_available)
                        },
                )

                MetadataLine(
                    label = stringResource(R.string.video_player_trip),
                    value = snapshot.tripSessionId ?: stringResource(R.string.common_not_available),
                )
            }
        }
    }
}

@Composable
private fun SegmentPlayerMetadataCard(
    item: DashcamVideoEntity,
) {
    val camera =
        when (item.cameraType) {
            DashcamCameraType.ROAD -> stringResource(R.string.video_player_camera_road)
            DashcamCameraType.DRIVER -> stringResource(R.string.video_player_camera_driver)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111318),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isEmergency || item.isProtected) {
                    ArchiveBadge(
                        text = stringResource(R.string.video_player_badge_protected),
                        color = Color(0xFFFF9F0A),
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                }

                ArchiveBadge(
                    text = camera.uppercase(),
                    color = Color(0xFF64D2FF),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.video_player_segment_details),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            MetadataLine(
                label = stringResource(R.string.video_player_started),
                value = formatVideoDate(item.startedAtMs),
            )

            MetadataLine(
                label = stringResource(R.string.video_player_duration),
                value = formatDuration(item.durationMs),
            )

            MetadataLine(
                label = stringResource(R.string.video_player_size),
                value = formatFileSize(item.sizeBytes),
            )

            MetadataLine(
                label = stringResource(R.string.video_player_clip),
                value = item.segmentIndex.toString(),
            )

            MetadataLine(
                label = stringResource(R.string.video_player_session),
                value = item.rollingSessionId ?: stringResource(R.string.common_not_available),
            )
        }
    }
}

@Composable
private fun CrashPlayerTimelineCard(
    item: CrashClipEntity,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111318),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.video_player_crash_timeline),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimelineDot(
                    label = "-${item.preCrashMs / 1000}s",
                    color = Color(0xFF8E8E93),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(Color(0xFF3A3A3C)),
                )

                TimelineDot(
                    label = stringResource(R.string.video_player_timeline_impact),
                    color = Color(0xFFFF453A),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(Color(0xFF3A3A3C)),
                )

                TimelineDot(
                    label = "+${item.postCrashMs / 1000}s",
                    color = Color(0xFF8E8E93),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.video_player_telemetry_samples_linked, item.telemetryTimeline.size),
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun UnknownVideoMetadataCard(
    videoPath: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111318),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.video_player_video),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            MetadataLine(
                label = stringResource(R.string.video_player_path),
                value = videoPath.substringAfterLast('/'),
            )
        }
    }
}

@Composable
private fun MetadataLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF8E8E93),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ArchiveBadge(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimelineDot(
    label: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
        )
    }
}

private fun formatVideoDate(
    timestamp: Long,
): String {
    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm:ss",
        Locale.getDefault(),
    ).format(Date(timestamp))
}

private fun formatDuration(
    durationMs: Long,
): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return String.format(
        Locale.US,
        "%02d:%02d",
        minutes,
        seconds,
    )
}

private fun formatFileSize(
    bytes: Long,
): String {
    val mb = bytes / 1024.0 / 1024.0

    return String.format(
        Locale.US,
        "%.1f MB",
        mb,
    )
}

private fun formatGForce(
    gForce: Double,
): String {
    return String.format(
        Locale.US,
        "%.1f",
        gForce,
    )
}