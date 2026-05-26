package com.alex.android_telemetry.ui.video

import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DashcamVideoItem(
    val id: String,
    val file: File,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
)

@Composable
fun VideoArchiveScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val context = LocalContext.current

    val videos = remember {
        loadDashcamVideos(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text("Назад")
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Архив видео",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (videos.isEmpty()) {
            EmptyVideoArchive()
        } else {
            Text(
                text = "${videos.size} записей",
                color = Color.Gray,
                fontSize = 15.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(videos) { item ->
                    DashcamVideoCard(
                        item = item,
                        onClick = {
                            onOpenVideo(item.file.absolutePath)
                        },
                    )
                }
            }
        }
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
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Записи появятся здесь после остановки видеорежима",
                color = Color.Gray,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun DashcamVideoCard(
    item: DashcamVideoItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
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
                    .background(Color(0xFF111827)),
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
                    text = formatVideoDate(item.createdAt),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Длительность: ${formatDuration(item.durationMs)}",
                    color = Color.Gray,
                    fontSize = 14.sp,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Размер: ${formatFileSize(item.sizeBytes)}",
                    color = Color.Gray,
                    fontSize = 14.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6)),
            )
        }
    }
}

private fun loadDashcamVideos(
    moviesDir: File?,
): List<DashcamVideoItem> {
    val dashcamDir = File(moviesDir, "dashcam")

    if (!dashcamDir.exists()) {
        return emptyList()
    }

    return dashcamDir
        .listFiles()
        .orEmpty()
        .filter { file ->
            file.isFile && file.extension.lowercase(Locale.US) == "mp4"
        }
        .sortedByDescending { file ->
            file.lastModified()
        }
        .map { file ->
            DashcamVideoItem(
                id = file.absolutePath,
                file = file,
                createdAt = file.lastModified(),
                durationMs = readVideoDurationMs(file),
                sizeBytes = file.length(),
            )
        }
}

private fun readVideoDurationMs(
    file: File,
): Long {
    return try {
        val retriever = MediaMetadataRetriever()

        retriever.setDataSource(file.absolutePath)

        val duration =
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

        retriever.release()

        duration
    } catch (_: Exception) {
        0L
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