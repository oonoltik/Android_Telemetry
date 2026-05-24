package com.alex.android_telemetry.ui.home

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.telemetry.trips.api.DriverHomeResponseDto
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography
import kotlinx.coroutines.delay

import kotlin.math.abs
import kotlin.math.roundToInt

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
    onOpenDriverAccount: () -> Unit,
    onOpenPermissionsBackground: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val isTripActive = state.telemetryMode != TelemetryMode.IDLE
    val hasDriver = !currentDriverId.isNullOrBlank()

    var homeMetrics by remember { mutableStateOf<DriverHomeResponseDto?>(null) }
    var homeMetricsError by remember { mutableStateOf<String?>(null) }
    var isLoadingHomeMetrics by remember { mutableStateOf(false) }

    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isTripActive, state.startedAt) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1000)
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
            homeMetricsError = throwable.message ?: "Не удалось загрузить рейтинг"
        }

        isLoadingHomeMetrics = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeToolbar(
            onOpenSettings = onOpenPermissionsBackground,
        )

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

        TripSummaryCard(
            state = state,
            nowEpochMs = nowEpochMs,
        )

        StartStopControls(
            canStart = !isTripActive && hasDriver,
            canStop = isTripActive,
            onStartTrip = onStartTrip,
            onStopTrip = onStopTrip,
        )

        ArchiveActions(
            onOpenTripsArchive = onOpenTripsArchive,
        )

        VideoModeButton()

        TrackingModeSegment()

        SaveFishButton()

        TextButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Диагностика",
                color = SwiftSecondaryText,
                style = TelemetryTypography.Callout,
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
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0x11000000),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 18.dp, vertical = 5.dp),
        ) {
            Text(
                text = "Настройки",
                color = Color.Black,
                style = TelemetryTypography.Title2,
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
        currentDriverId.isNullOrBlank() -> "Водитель не выбран"
        isLoading -> "Загружаем рейтинг…"
        errorText != null -> "Рейтинг временно недоступен"
        metrics?.driverLevel != null -> localizedDriverLevel(metrics.driverLevel)
        metrics?.ratingStatus == "forming" -> "Рейтинг формируется"
        metrics != null -> "Рейтинг рассчитан"
        else -> "Рейтинг появится после поездок"
    }

    val ratingFormingText = if (metrics?.ratingStatus == "forming") {
        "Рейтинг формируется · осталось поездок: ${metrics.tripsToUnlockPercentile}"
    } else {
        null
    }

    val deltaText = formatScoreDelta(metrics?.scoreDeltaRecent)
    val percentileText = formatPercentile(metrics)
    val nextLevelText = formatLevelText(metrics)

    SwiftCard(
        cornerRadius = 18,
        padding = 24,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Оценка вождения",
                color = Color.Black,
                style = TelemetryTypography.Title1,
                textAlign = TextAlign.Center,
            )

            Text(
                text = scoreText,
                color = SwiftBlue,
                style = TelemetryTypography.ScoreHero,
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
                    text = "Водитель: $currentDriverId",
                    color = SwiftSecondaryText,
                    style = TelemetryTypography.Body,
                    textAlign = TextAlign.Center,
                )
            }

            if (deltaText != null) {
                Text(
                    text = deltaText,
                    color = if ((metrics?.scoreDeltaRecent ?: 0.0) >= 0.0) SwiftGreen else SwiftOrange,
                    style = TelemetryTypography.Title2,
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
                    style = TelemetryTypography.Title2,
                    textAlign = TextAlign.Center,
                )
            }

            if (nextLevelText != null) {
                Text(
                    text = nextLevelText,
                    color = SwiftSecondaryText,
                    style = TelemetryTypography.Body,
                    textAlign = TextAlign.Center,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecentTripDots(
                            scores = metrics.recentTripScores,
                            colors = metrics.recentTripColors,
                        )

                        Text(
                            text = recentTripsSummary(metrics.recentTripScores),
                            color = Color.Black,
                            style = TelemetryTypography.Title2,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = "Сохраните зелёную серию в следующей поездке",
                            color = SwiftSecondaryText,
                            style = TelemetryTypography.Body,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (errorText != null) {
                Text(
                    text = "Данные рейтинга временно недоступны",
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
        padding = 18,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )

            Text(
                text = if (isTripActive) "Запись" else "Готово",
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
        padding = 18,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TripSummaryRow(
                icon = "◴",
                label = "Текущая скорость",
                value = "${formatOneDecimal((state.currentSpeedMS ?: 0.0) * 3.6)} km/h",
            )

            TripSummaryRow(
                icon = "↻",
                label = "Время поездки",
                value = formatElapsed(elapsedSec),
            )

            TripSummaryRow(
                icon = "▤",
                label = "Дистанция",
                value = "${formatTwoDecimals(distanceKm)} км",
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
            modifier = Modifier.width(34.dp),
            color = SwiftSecondaryText,
            style = TelemetryTypography.Title2,
        )

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = SwiftSecondaryText,
            style = TelemetryTypography.Title2,
        )

        Text(
            text = value,
            color = Color.Black,
            style = TelemetryTypography.Title2,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StartStopControls(
    canStart: Boolean,
    canStop: Boolean,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onStartTrip,
            enabled = canStart,
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SwiftBlue,
                disabledContainerColor = SwiftButtonGray,
                contentColor = Color.White,
                disabledContentColor = Color(0xFFB8B8BE),
            ),
        ) {
            Text(
                text = "Старт",
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
                text = "Стоп",
                style = TelemetryTypography.Title2,
            )
        }
    }
}

@Composable
private fun ArchiveActions(
    onOpenTripsArchive: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton(
            text = "Архив поездок",
            modifier = Modifier.weight(1f),
            height = 58,
            onClick = onOpenTripsArchive,
        )

        PillButton(
            text = "Архив видео",
            modifier = Modifier.weight(1f),
            height = 58,
            onClick = {},
        )
    }
}
@Composable
private fun VideoModeButton() {
    PillButton(
        text = "Видеорежим",
        modifier = Modifier.fillMaxWidth(),
        height = 64,
        onClick = {},
    )
}

@Composable
private fun TrackingModeSegment() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SwiftButtonGray)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Дорога",
                color = Color.Black,
                style = TelemetryTypography.Headline,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Водитель",
                color = Color.Black,
                style = TelemetryTypography.Headline,
            )
        }
    }
}

@Composable
private fun SaveFishButton() {
    PillButton(
        text = "≋  Игра Спаси Рыбку",
        modifier = Modifier.fillMaxWidth(),
        height = 64,
        onClick = {},
    )
}

@Composable
private fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Int = 58,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height.dp),
        shape = RoundedCornerShape((height / 2).dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SwiftButtonGray,
            contentColor = SwiftBlue,
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

private fun formatScoreDelta(value: Double?): String? {
    if (value == null) return null

    val rounded = formatOneDecimal(abs(value))
    return when {
        value > 0.0 -> "↗ +$rounded за последние 5 поездок"
        value < 0.0 -> "↘ -$rounded за последние 5 поездок"
        else -> "→ без изменений за последние 5 поездок"
    }
}

private fun formatPercentile(metrics: DriverHomeResponseDto?): String? {
    if (metrics == null) return null

    val pct = metrics.betterThanDriversPct
    if (pct != null) {
        val roundedPct = pct.roundToInt()

        return if (roundedPct <= 50) {
            "Лучше $roundedPct% водителей"
        } else {
            "Топ ${maxOf(1, 100 - roundedPct)}% водителей"
        }
    }

    val rank = metrics.driverRank
    val total = metrics.totalDrivers
    if (rank != null && total > 0) {
        return "Место $rank из $total"
    }

    return null
}

private fun formatLevelText(metrics: DriverHomeResponseDto?): String? {
    if (metrics == null) return null

    val nextLevel = metrics.nextLevel
    val points = metrics.pointsToNextLevel

    return when {
        nextLevel != null && points != null -> {
            "До уровня ${localizedDriverLevel(nextLevel)} осталось ${formatOneDecimal(points)}"
        }
        metrics.driverLevel != null -> {
            localizedDriverLevel(metrics.driverLevel)
        }
        else -> null
    }
}

private fun recentTripsSummary(scores: List<Double>): String {
    if (scores.isEmpty()) return "Недавних поездок пока нет"

    val last = scores.last()
    val best = scores.maxOrNull()

    return if (best != null && last >= best) {
        "Последняя поездка — лучшая в серии"
    } else {
        "Есть поездка, которую можно улучшить"
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

private fun localizedDriverLevel(raw: String): String {
    return when (raw.trim().lowercase()) {
        "risky_driver", "risky driver", "risky" -> "Рискованный водитель"
        "average_driver", "average driver", "average" -> "Средний водитель"
        "calm_driver", "calm driver", "calm" -> "Спокойный водитель"
        "safe_driver", "safe driver", "safe" -> "Безопасный водитель"
        else -> raw
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