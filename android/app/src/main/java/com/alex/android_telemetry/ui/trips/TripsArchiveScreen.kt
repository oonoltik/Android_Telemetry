package com.alex.android_telemetry.ui.trips

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TripsArchiveScreen(
    tripApi: TripApi,
    deviceId: String,
    driverId: String,
    onBack: () -> Unit,
) {
    var trips by remember { mutableStateOf<List<TripSummaryDto>>(emptyList()) }
    var selectedTrip by remember { mutableStateOf<TripSummaryDto?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun loadArchive() {
        scope.launch {
            loading = true
            error = null

            try {
                trips = tripApi.fetchRecentTrips(
                    deviceId = deviceId,
                    driverId = driverId,
                    limit = 30,
                )
                loadedOnce = true
            } catch (t: Throwable) {
                trips = emptyList()
                error = localizedArchiveError(t)
                loadedOnce = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(deviceId, driverId) {
        loadArchive()
    }

    selectedTrip?.let { trip ->
        TripReportScreen(
            tripApi = tripApi,
            deviceId = deviceId,
            trip = trip,
            onBack = { selectedTrip = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F1F4))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArchiveHeader(onBack = onBack)

        ArchiveStateSheet(
            loading = loading,
            loadedOnce = loadedOnce,
            count = trips.size,
            error = error,
            onRefresh = { loadArchive() },
        )

        trips.forEach { trip ->
            ArchiveTripRow(
                trip = trip,
                onOpen = { selectedTrip = trip },
            )
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
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = "Закрыть",
                color = Color(0xFFF28C28),
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.width(18.dp))

        Text(
            text = "Архив поездок",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun ArchiveStateSheet(
    loading: Boolean,
    loadedOnce: Boolean,
    count: Int,
    error: String?,
    onRefresh: () -> Unit,
) {
    SwiftReportSheet {
        when {
            loading && !loadedOnce -> {
                CircularProgressIndicator()
                SwiftReportSectionTitle("Загрузка архива…")
                SwiftReportCaption("Получаем последние поездки.")
            }

            error != null -> {
                SwiftReportSectionTitle("Архив недоступен")
                SwiftReportCaption(error)
                SwiftOrangeButton("Обновить", onClick = onRefresh)
            }

            loadedOnce && count == 0 -> {
                SwiftReportSectionTitle("Поездок пока нет")
                SwiftReportCaption("После завершения поездки и построения отчёта она появится здесь.")
                SwiftOrangeButton("Обновить", onClick = onRefresh)
            }

            else -> {
                SwiftReportSectionTitle("Последние поездки")
                SwiftReportCaption("Найдено поездок: $count")
                SwiftOrangeButton("Обновить", onClick = onRefresh)
            }
        }
    }
}

@Composable
private fun ArchiveTripRow(
    trip: TripSummaryDto,
    onOpen: () -> Unit,
) {
    val score = trip.scoreV2 ?: trip.tripScore
    val endedAt = trip.receivedEndedAt ?: trip.clientEndedAt ?: "—"

    SwiftReportSheet {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScorePill(score)

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = endedAt,
                    color = Color.Black,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                SwiftReportCaption("Режим: ${localizedDrivingModeRu(trip.drivingMode)}")
                SwiftReportCaption("Дистанция: ${formatKmRu(trip.distanceKm)}")
                SwiftReportCaption("Средняя скорость: ${formatSpeedRu(trip.avgSpeedKmh)}")
            }
        }

        SwiftOrangeButton("Открыть отчёт", onClick = onOpen)
    }
}

@Composable
private fun TripReportScreen(
    tripApi: TripApi,
    deviceId: String,
    trip: TripSummaryDto,
    onBack: () -> Unit,
) {
    var report by remember { mutableStateOf<TripReportDto?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pollingIteration by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    fun loadReport() {
        scope.launch {
            loading = true
            error = null

            try {
                report = tripApi.fetchTripReport(
                    deviceId = deviceId,
                    sessionId = trip.sessionId,
                    driverId = trip.driverId.orEmpty(),
                )
            } catch (t: Throwable) {
                error = localizedArchiveError(t)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(trip.sessionId) {
        loadReport()
    }

    LaunchedEffect(report?.sessionId, report?.batchesMissingCount) {
        while ((report?.batchesMissingCount ?: 0) > 0) {
            pollingIteration += 1
            delay(if (pollingIteration < 10) 2_000L else 5_000L)
            loadReport()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F1F4))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SwiftReportHeader(onBack = onBack)

        if (loading && report == null) {
            SwiftReportSheet {
                CircularProgressIndicator()
                SwiftReportSectionTitle("Загрузка отчёта…")
            }
            return@Column
        }

        if (error != null && report == null) {
            SwiftReportSheet {
                SwiftReportSectionTitle("Отчёт недоступен")
                SwiftReportCaption(error ?: "Ошибка")
                SwiftOrangeButton("Обновить", onClick = { loadReport() })
            }
            return@Column
        }

        report?.let { r ->
            SwiftReportMainSheet(
                report = r,
                onRefresh = { loadReport() },
            )
        }
    }
}

@Composable
private fun SwiftReportHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = "Закрыть",
                color = Color(0xFFF28C28),
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.width(18.dp))

        Text(
            text = "Отчёт о поездке",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun SwiftReportMainSheet(
    report: TripReportDto,
    onRefresh: () -> Unit,
) {
    val score = report.scoreV2 ?: report.tripScore
    var showImpactDetails by remember { mutableStateOf(false) }
    var showFullDetails by remember { mutableStateOf(false) }
    val missing = report.batchesMissingCount ?: 0
    val dangerousManeuvers =
        report.accelSharpTotal +
                report.accelEmergencyTotal +
                report.brakeSharpTotal +
                report.brakeEmergencyTotal +
                report.turnSharpTotal +
                report.turnEmergencyTotal

    val skidRisk = report.accelInTurnTotal + report.brakeInTurnTotal
    val roadAnomalies = report.roadAnomalyLowTotal + report.roadAnomalyHighTotal

    SwiftReportSheet {
        if (missing > 0) {
            SwiftReportSectionTitle("Предварительный отчёт")
            SwiftReportCaption("Сервер ещё обрабатывает батчи: $missing")
            SwiftReportCaption("Готовность: ${(reportCoverage(report) * 100).toInt()}%")
            ProgressBar(reportCoverage(report))
            SwiftDivider()
        }

        Text(
            text = "Рейтинг этой поездки",
            color = Color(0xFF8A8A8E),
            fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "${formatScore(score)} / 100",
            color = Color(0xFFF28C28),
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = localizedDrivingModeRu(report.drivingMode),
            color = Color(0xFF8A8A8E),
            fontSize = 21.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🚗 ${scoreLabelRu(score)}",
                color = Color(0xFFF28C28),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = scoreHintRu(score),
                color = Color(0xFF8A8A8E),
                fontSize = 20.sp,
            )
        }

        SwiftDivider()

        SwiftReportMetricRow("⏱", "Длительность поездки", formatDuration(report.tripDurationSec))
        SwiftReportMetricRow("🛣", "Дистанция", formatKmRu(report.distanceKm))
        SwiftReportMetricRow("⏲", "Средняя скорость", formatSpeedRu(report.avgSpeedKmh))
        SwiftReportMetricRow("🚘", "Режим поездки", localizedDrivingModeRu(report.drivingMode))

        SwiftDivider()

        SwiftReportMetricRow("🧭", "Интенсивность вождения", "—")

        SwiftDivider()

        SwiftReportMetricRow("🚗", "Ваш средний рейтинг", "—")
        SwiftReportMetricRow("ℹ", "Учитываемых поездок у вас", "0")

        SwiftDivider()

        Text(
            text = "Лучше, чем 0% поездок всех водителей",
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        SwiftReportMetricRow("▮", "Всего учтено поездок", "253")

        SwiftDivider()

        Text(
            text = "Сводка событий",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwiftEventSummaryCard(
                icon = "⚠",
                title = "Опасные\nманёвры",
                value = dangerousManeuvers.toString(),
                modifier = Modifier.weight(1f),
            )
            SwiftEventSummaryCard(
                icon = "❄",
                title = "Риск\nзаноса",
                value = skidRisk.toString(),
                modifier = Modifier.weight(1f),
            )
            SwiftEventSummaryCard(
                icon = "!",
                title = "Неровности",
                value = roadAnomalies.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        TextButton(
            onClick = { showImpactDetails = !showImpactDetails },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Что повлияло на оценку",
                    color = Color.Black,
                    fontSize = 22.sp,
                )
                Text(
                    text = if (showImpactDetails) "⌃" else "⌄",
                    color = Color.Black,
                    fontSize = 26.sp,
                )
            }
        }

        if (showImpactDetails) {
            SwiftImpactRow("⚠", "Ускорения (резкие)", report.accelSharpTotal)
            SwiftImpactRow("!", "Ускорения (экстренные)", report.accelEmergencyTotal)
            SwiftImpactRow("■", "Торможения (резкие)", report.brakeSharpTotal)
            SwiftImpactRow("●", "Торможения (экстренные)", report.brakeEmergencyTotal)
            SwiftImpactRow("↪", "Повороты (резкие)", report.turnSharpTotal)
            SwiftImpactRow("↩", "Повороты (экстренные)", report.turnEmergencyTotal)
            SwiftImpactRow("❄", "Ускорение в повороте", report.accelInTurnTotal)
            SwiftImpactRow("❄", "Торможение в повороте", report.brakeInTurnTotal)
            SwiftImpactRow("!", "Неровности (низкие)", report.roadAnomalyLowTotal)
            SwiftImpactRow("!", "Неровности (сильные)", report.roadAnomalyHighTotal)
        }

        SwiftDivider()

        if (showFullDetails) {
            SwiftDivider()

            Text(
                text = "Остановки",
                color = Color(0xFF8A8A8E),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            SwiftReportMetricRow("", "Количество остановок", "0")
            SwiftReportMetricRow("", "Общее время остановок", "0.0 сек")
            SwiftReportMetricRow("", "Длительность остановки (P95)", "0.0 сек")
            SwiftReportMetricRow("", "Остановок на км", "0.000")

            SwiftDivider()

            Text(
                text = "Сравнение",
                color = Color(0xFF8A8A8E),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            SwiftReportMetricRow("", "Лучше ваших предыдущих поездок", "—")
            SwiftReportMetricRow("", "Лучше всех поездок всех водителей", "0.0%")
            SwiftReportMetricRow("", "Поездок ранее (этот водитель)", "0")
            SwiftReportMetricRow("", "Всего поездок (в базе)", "253")
        }

        SwiftOrangeButton(
            text = if (showFullDetails) "Скрыть подробности" else "Подробнее",
            onClick = { showFullDetails = !showFullDetails },
        )
    }
}

@Composable
private fun SwiftReportSheet(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        content()
    }
}

@Composable
private fun SwiftReportSectionTitle(text: String) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SwiftReportCaption(text: String) {
    Text(
        text = text,
        color = Color(0xFF8A8A8E),
        fontSize = 18.sp,
    )
}

@Composable
private fun SwiftDivider() {
    HorizontalDivider(
        color = Color(0xFFE0E0E0),
        thickness = 1.dp,
    )
}

@Composable
private fun ProgressBar(value: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(8.dp)
                .background(Color(0xFFF28C28), RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun SwiftReportMetricRow(
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
            fontSize = 26.sp,
            color = Color(0xFF8A8A8E),
            modifier = Modifier.width(54.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = Color(0xFF8A8A8E),
                fontSize = 21.sp,
            )
            Text(
                text = value,
                color = Color.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SwiftEventSummaryCard(
    icon: String,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(150.dp)
            .background(Color(0xFFF4F4F5), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = icon,
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = title,
            color = Color(0xFF8A8A8E),
            fontSize = 17.sp,
        )
        Text(
            text = value,
            color = Color.Black,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SwiftImpactRow(
    icon: String,
    label: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = "$label: $count",
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SwiftOrangeButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF28C28),
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ScorePill(score: Double?) {
    Column(
        modifier = Modifier
            .background(Color(0xFFFFEBEE), RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatScore(score),
            color = Color(0xFFFF3B30),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "/100",
            color = Color(0xFF8A8A8E),
            fontSize = 13.sp,
        )
    }
}

private fun reportCoverage(report: TripReportDto?): Float {
    if (report == null) return 0f

    val total = report.batchesCount
    val missing = report.batchesMissingCount ?: 0

    if (total <= 0) return 0f

    return ((total - missing).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun localizedDrivingModeRu(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "mixed" -> "Смешанный"
        "city" -> "Город"
        "highway" -> "Трасса"
        else -> "Город"
    }
}

private fun formatScore(score: Double?): String {
    return score?.let { "%.0f".format(it) } ?: "—"
}

private fun formatKmRu(value: Double?): String {
    return value?.let { "%.2f км".format(it) } ?: "—"
}

private fun formatSpeedRu(value: Double?): String {
    return value?.let { "%.1f км/ч".format(it) } ?: "—"
}

private fun formatDuration(seconds: Double?): String {
    val total = seconds?.toLong()?.takeIf { it > 0 } ?: return "—"
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60

    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private fun scoreLabelRu(score: Double?): String {
    if (score == null) return "—"

    return when {
        score >= 80.0 -> "Хорошо"
        score >= 60.0 -> "Нормально"
        else -> "Плохо"
    }
}

private fun scoreHintRu(score: Double?): String {
    if (score == null) return "Нет данных"

    return when {
        score >= 80.0 -> "Отлично"
        score >= 60.0 -> "Можно лучше"
        else -> "Нужно аккуратнее"
    }
}

private fun localizedArchiveError(error: Throwable): String {
    val raw = error.message.orEmpty().lowercase()

    return when {
        raw.contains("device is not authorized for this driver_id") ->
            "Устройство не авторизовано для этого driver_id"

        raw.contains("unauthorized") || raw.contains("401") ->
            "Сессия не авторизована. Проверь driver/account login."

        raw.contains("not found") || raw.contains("404") ->
            "Архив или отчёт пока не найден"

        raw.contains("timeout") ->
            "Сервер не ответил вовремя"

        else ->
            "Ошибка загрузки: ${error.message ?: "unknown"}"
    }
}