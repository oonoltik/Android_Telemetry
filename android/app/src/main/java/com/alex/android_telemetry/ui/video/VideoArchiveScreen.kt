package com.alex.android_telemetry.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import java.io.File
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState

private enum class ArchiveFilter {
    ALL,
    EMERGENCY,
    REGULAR,
    FAILED_UPLOADS,
}

@Composable
fun VideoArchiveScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val context = LocalContext.current

    val videoRepository =
        remember {
            DashcamVideoRepository(context)
        }

    val crashClipRepository =
        remember {
            CrashClipRepository(
                context = context,
                videoRepository = videoRepository,
            )
        }

    var filter by remember {
        mutableStateOf(ArchiveFilter.ALL)
    }

    var videos by remember {
        mutableStateOf(videoRepository.loadVideos())
    }

    var crashClips by remember {
        mutableStateOf(crashClipRepository.loadCrashClips())
    }

    val archiveVersion by
    DashcamArchiveRefreshBus.version.collectAsState()

    LaunchedEffect(archiveVersion) {
        videos = videoRepository.loadVideos()
        crashClips = crashClipRepository.loadCrashClips()
    }

    val stats =
        videoRepository.storageStats()

    val regularVideos =
        videos.filterNot {
            it.isEmergency || it.isProtected
        }

    val visibleCrashClips =
        when (filter) {
            ArchiveFilter.ALL,
            ArchiveFilter.EMERGENCY -> crashClips

            ArchiveFilter.REGULAR -> emptyList()

            ArchiveFilter.FAILED_UPLOADS ->
                crashClips.filter {
                    it.uploadState == CrashClipUploadState.FAILED
                }
        }

    val visibleRegularVideos =
        when (filter) {
            ArchiveFilter.ALL,
            ArchiveFilter.REGULAR -> regularVideos

            ArchiveFilter.EMERGENCY,
            ArchiveFilter.FAILED_UPLOADS -> emptyList()
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070A))
            .padding(16.dp),
    ) {
        ArchiveHeader(
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(18.dp))

        StorageSummaryCard(
            stats = stats,
        )

        Spacer(modifier = Modifier.height(14.dp))

        ArchiveFilterRow(
            selected = filter,
            onSelect = {
                filter = it
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (visibleCrashClips.isEmpty() && visibleRegularVideos.isEmpty()) {
            EmptyVideoArchive()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (visibleCrashClips.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = "EMERGENCY",
                            count = visibleCrashClips.size,
                            color = Color(0xFFFF453A),
                        )
                    }

                    items(visibleCrashClips) { item ->
                        CrashClipCard(
                            item = item,
                            onClick = {
                                item.mergedClipPath?.let(onOpenVideo)
                            },
                            onExport = {
                                val exported =
                                    item.mergedClipPath?.let { path ->
                                        videoRepository.exportFileToGallery(
                                            sourceFile = File(path),
                                            displayName = "${item.crashId}.mp4",
                                        )
                                    } ?: false

                                Toast
                                    .makeText(
                                        context,
                                        if (exported) {
                                            "Видео сохранено в галерею"
                                        } else {
                                            "Не удалось сохранить видео"
                                        },
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            },
                            onDelete = {
                                val deleted =
                                    crashClipRepository.deleteCrashClip(
                                        item.crashId,
                                    )

                                if (deleted) {
                                    crashClips = crashClipRepository.loadCrashClips()
                                }

                                Toast
                                    .makeText(
                                        context,
                                        if (deleted) {
                                            "Аварийный клип удалён"
                                        } else {
                                            "Не удалось удалить клип"
                                        },
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            },
                        )
                    }
                }

                if (visibleRegularVideos.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = "REGULAR",
                            count = visibleRegularVideos.size,
                            color = Color(0xFF64D2FF),
                        )
                    }

                    items(visibleRegularVideos) { item ->
                        DashcamVideoCard(
                            item = item,
                            onClick = {
                                onOpenVideo(item.absolutePath)
                            },
                            onExport = {
                                val exported =
                                    videoRepository.exportVideoToGallery(item)

                                Toast
                                    .makeText(
                                        context,
                                        if (exported) {
                                            "Видео сохранено в галерею"
                                        } else {
                                            "Не удалось сохранить видео"
                                        },
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            },
                            onDelete = {
                                val deleted =
                                    videoRepository.deleteVideo(item)

                                if (deleted) {
                                    videos = videoRepository.loadVideos()
                                }

                                Toast
                                    .makeText(
                                        context,
                                        if (deleted) {
                                            "Видео удалено"
                                        } else {
                                            "Защищённое видео нельзя удалить"
                                        },
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            videoRepository.applyRetentionPolicy()
                            videos = videoRepository.loadVideos()
                            crashClips = crashClipRepository.loadCrashClips()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Apply retention cleanup")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveHeader(
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
            Text("Назад")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Dashcam Archive",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StorageSummaryCard(
    stats: DashcamStorageStats,
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
                text = "Storage",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${formatFileSize(stats.totalBytes)} / ${formatFileSize(stats.maxBytes)}",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Protected: ${formatFileSize(stats.protectedBytes)} • Files: ${stats.filesCount}",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveFilterRow(
    selected: ArchiveFilter,
    onSelect: (ArchiveFilter) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArchiveFilterButton(
            text = "All",
            selected = selected == ArchiveFilter.ALL,
            onClick = {
                onSelect(ArchiveFilter.ALL)
            },
        )

        ArchiveFilterButton(
            text = "Emergency",
            selected = selected == ArchiveFilter.EMERGENCY,
            onClick = {
                onSelect(ArchiveFilter.EMERGENCY)
            },
        )

        ArchiveFilterButton(
            text = "Regular",
            selected = selected == ArchiveFilter.REGULAR,
            onClick = {
                onSelect(ArchiveFilter.REGULAR)
            },
        )

        ArchiveFilterButton(
            text = "Failed uploads",
            selected = selected == ArchiveFilter.FAILED_UPLOADS,
            onClick = {
                onSelect(ArchiveFilter.FAILED_UPLOADS)
            },
        )
    }
}

@Composable
private fun ArchiveFilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (selected) {
                    Color(0xFF0A84FF)
                } else {
                    Color(0xFF1C1C1E)
                },
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "$title • $count",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CrashClipCard(
    item: CrashClipEntity,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val snapshot =
        item.telemetrySnapshot

    val hasMergedClip =
        !item.mergedClipPath.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = hasMergedClip,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A0D0D),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            if (hasMergedClip) {
                                "⚠"
                            } else {
                                "…"
                            },
                        color = Color.White,
                        fontSize = 28.sp,
                    )
                }

                Spacer(modifier = Modifier.size(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = formatVideoDate(item.detectedAtMs),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Impact • ${formatGForce(item.gForce)}g",
                        color = Color(0xFFFF9F0A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${item.segmentPaths.size} segments • ${formatUploadState(item.uploadState)}",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BadgeRow(
                item = item,
            )

            Spacer(modifier = Modifier.height(12.dp))

            CrashTelemetrySummary(
                item = item,
            )

            Spacer(modifier = Modifier.height(12.dp))

            CrashTimelinePreview(
                item = item,

            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Export")
                }

                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3A1D1D),
                        contentColor = Color(0xFFFF453A),
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(
    item: CrashClipEntity,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArchiveBadge(
            text = "EMERGENCY",
            color = Color(0xFFFF453A),
        )

        ArchiveBadge(
            text = "PROTECTED",
            color = Color(0xFFFF9F0A),
        )

        ArchiveBadge(
            text =
                when (item.uploadState) {
                    CrashClipUploadState.UPLOADED -> "SYNCED"
                    CrashClipUploadState.UPLOADING -> "SYNCING"
                    CrashClipUploadState.QUEUED -> "QUEUED"
                    CrashClipUploadState.FAILED -> "FAILED"
                    CrashClipUploadState.LOCAL_ONLY -> "LOCAL"
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
private fun CrashTelemetrySummary(
    item: CrashClipEntity,
) {
    val snapshot =
        item.telemetrySnapshot

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111318),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "Telemetry",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    if (snapshot == null) {
                        "No telemetry snapshot"
                    } else {
                        buildString {
                            append(formatGForce(item.gForce))
                            append("g")
                            append(" • ")

                            append(
                                snapshot.speedKmh?.let {
                                    "${it.toInt()} km/h"
                                } ?: "speed n/a"
                            )

                            append(" • ")

                            append(
                                snapshot.headingDeg?.let {
                                    "${it.toInt()}°"
                                } ?: "heading n/a"
                            )
                        }
                    },
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text =
                    if (snapshot?.lat != null && snapshot.lon != null) {
                        String.format(
                            Locale.US,
                            "%.5f, %.5f",
                            snapshot.lat,
                            snapshot.lon,
                        )
                    } else {
                        "GPS n/a"
                    },
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CrashTimelinePreview(
    item: CrashClipEntity,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineDot(
                label = "-10s",
                color = Color(0xFF8E8E93),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(Color(0xFF3A3A3C)),
            )

            TimelineDot(
                label = "impact",
                color = Color(0xFFFF453A),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(Color(0xFF3A3A3C)),
            )

            TimelineDot(
                label = "+10s",
                color = Color(0xFF8E8E93),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${item.telemetryTimeline.size} telemetry samples linked",
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
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

@Composable
private fun EmptyVideoArchive() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Видеоархив пуст",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Записи и аварийные клипы появятся здесь",
                color = Color(0xFF8E8E93),
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun DashcamVideoCard(
    item: DashcamVideoEntity,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val cameraLabel =
        when (item.cameraType) {
            DashcamCameraType.ROAD -> "Дорога"
            DashcamCameraType.DRIVER -> "Водитель"
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111318),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 26.sp,
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = formatVideoDate(item.startedAtMs),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$cameraLabel • clip ${item.segmentIndex}",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${formatDuration(item.durationMs)} • ${formatFileSize(item.sizeBytes)}",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isEmergency || item.isProtected) {
                            Color(0xFFFF453A)
                        } else {
                            Color(0xFF64D2FF)
                        }
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 14.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Export")
            }

            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A1D1D),
                    contentColor = Color(0xFFFF453A),
                ),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Delete")
            }
        }
    }
}

private fun formatUploadState(
    state: CrashClipUploadState,
): String {
    return when (state) {
        CrashClipUploadState.LOCAL_ONLY -> "local"
        CrashClipUploadState.QUEUED -> "queued"
        CrashClipUploadState.UPLOADING -> "syncing"
        CrashClipUploadState.UPLOADED -> "synced"
        CrashClipUploadState.FAILED -> "failed"
    }
}

private fun formatVideoDate(
    timestamp: Long,
): String {
    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
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