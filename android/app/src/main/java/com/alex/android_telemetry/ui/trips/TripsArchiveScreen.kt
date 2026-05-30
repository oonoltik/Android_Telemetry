package com.alex.android_telemetry.ui.trips

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.android_telemetry.telemetry.trips.api.TripApi
import com.alex.android_telemetry.telemetry.trips.api.TripReportDto
import com.alex.android_telemetry.telemetry.trips.api.TripSummaryDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.text.style.TextAlign
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ColumnScope
import com.alex.android_telemetry.R




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
    val context = LocalContext.current

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
                error = localizedArchiveError(context, t)
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
            archiveTrips = trips,
            onBack = { selectedTrip = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ArchiveHeader(onBack = onBack)

        when {
            loading && !loadedOnce -> {
                SwiftArchiveListSheet {
                    CircularProgressIndicator()
                    SwiftReportCaption(stringResource(R.string.trips_archive_loading))
                }
            }

            error != null -> {
                SwiftArchiveListSheet {
                    SwiftReportSectionTitle(stringResource(R.string.trips_archive_unavailable))
                    SwiftReportCaption(error.orEmpty())
                    SwiftOrangeButton(stringResource(R.string.trips_common_refresh), onClick = { loadArchive() })
                }
            }

            loadedOnce && trips.isEmpty() -> {
                SwiftArchiveListSheet {
                    SwiftReportSectionTitle(stringResource(R.string.trips_archive_empty_title))
                    SwiftReportCaption(stringResource(R.string.trips_archive_empty_caption))
                    SwiftOrangeButton(stringResource(R.string.trips_common_refresh), onClick = { loadArchive() })
                }
            }

            else -> {
                ArchiveTripsList(
                    trips = trips,
                    onOpen = { selectedTrip = it },
                )
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
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(32.dp))
                .padding(horizontal = 4.dp, vertical = 10.dp),
        ) {
            Text(
                text = "‹",
                color = Color.Black,
                fontSize = 42.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        Spacer(Modifier.width(18.dp))

        Text(
            text = stringResource(R.string.trips_archive_header),
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 27.sp,
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
                SwiftReportSectionTitle(stringResource(R.string.trips_archive_loading))
                SwiftReportCaption(stringResource(R.string.trips_archive_loading_caption))
            }

            error != null -> {
                SwiftReportSectionTitle(stringResource(R.string.trips_archive_unavailable))
                SwiftReportCaption(error)
                SwiftOrangeButton(stringResource(R.string.trips_common_refresh), onClick = onRefresh)
            }

            loadedOnce && count == 0 -> {
                SwiftReportSectionTitle(stringResource(R.string.trips_archive_empty_title))
                SwiftReportCaption(stringResource(R.string.trips_archive_empty_report_caption))
                SwiftOrangeButton(stringResource(R.string.trips_common_refresh), onClick = onRefresh)
            }

            else -> {
                SwiftReportSectionTitle(stringResource(R.string.trips_archive_recent_title))
                SwiftReportCaption(stringResource(R.string.trips_archive_found_count, count))
                SwiftOrangeButton(stringResource(R.string.trips_common_refresh), onClick = onRefresh)
            }
        }
    }
}

@Composable
private fun ArchiveTripRow(
    trip: TripSummaryDto,
    showDivider: Boolean,
    onOpen: () -> Unit,
) {
    val score = trip.scoreV2 ?: trip.tripScore
    val dateText = formatArchiveDate(
        trip.receivedEndedAt ?: trip.clientEndedAt ?: trip.receivedStartedAt ?: trip.clientStartedAt,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(13.dp)
                    .height(13.dp)
                    .background(
                        color = archiveScoreColor(score),
                        shape = RoundedCornerShape(999.dp),
                    ),
            )

            Spacer(Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.trips_archive_date_format, dateText),
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.trips_archive_score_format, formatScoreTwoDecimals(score)),
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                    )

                    Text(
                        text = localizedDrivingModeRu(trip.drivingMode),
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                    )

                    Text(
                        text = formatKmRu(trip.distanceKm),
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                    )
                }
            }

            Text(
                text = "›",
                color = Color(0xFFC7C7CC),
                fontSize = 38.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp),
                color = Color(0xFFE0E0E0),
                thickness = 1.dp,
            )
        }
    }
}

@Composable
private fun ArchiveTripsList(
    trips: List<TripSummaryDto>,
    onOpen: (TripSummaryDto) -> Unit,
) {
    SwiftArchiveListSheet {
        trips.forEachIndexed { index, trip ->
            ArchiveTripRow(
                trip = trip,
                showDivider = index != trips.lastIndex,
                onOpen = { onOpen(trip) },
            )
        }
    }
}

@Composable
private fun SwiftArchiveListSheet(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        content()
    }
}

@Composable
private fun TripReportScreen(
    tripApi: TripApi,
    deviceId: String,
    trip: TripSummaryDto,
    archiveTrips: List<TripSummaryDto>,
    onBack: () -> Unit,
) {
    var report by remember { mutableStateOf<TripReportDto?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pollingIteration by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                error = localizedArchiveError(context, t)
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
                SwiftReportSectionTitle(stringResource(R.string.trip_report_loading))
            }
            return@Column
        }

        if (error != null && report == null) {
            SwiftReportSheet {
                SwiftReportSectionTitle(stringResource(R.string.trip_report_unavailable))
                SwiftReportCaption(error ?: stringResource(R.string.trips_common_error))
                SwiftOrangeButton(stringResource(R.string.trips_common_refresh), onClick = { loadReport() })
            }
            return@Column
        }

        report?.let { r ->
            SwiftReportMainSheet(
                report = r,
                archiveTrips = archiveTrips,
                currentTrip = trip,
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
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.trips_common_close),
                color = Color(0xFFF28C28),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(22.dp))

        Text(
            text = stringResource(R.string.trip_report_title),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun SwiftReportMainSheet(
    report: TripReportDto,
    archiveTrips: List<TripSummaryDto>,
    currentTrip: TripSummaryDto,
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

    val comparison = remember(report.sessionId, archiveTrips) {
        calculateTripComparison(
            currentTrip = currentTrip,
            currentScore = score,
            archiveTrips = archiveTrips,
        )
    }

    SwiftReportSheet {
        if (missing > 0) {
            SwiftReportSectionTitle(stringResource(R.string.trip_report_preliminary_title))
            SwiftReportCaption(stringResource(R.string.trip_report_batches_processing, missing))
            SwiftReportCaption(stringResource(R.string.trip_report_readiness_percent, (reportCoverage(report) * 100).toInt()))
            ProgressBar(reportCoverage(report))
            SwiftDivider()
        }

        Text(
            text = stringResource(R.string.trip_report_trip_rating),
            color = Color(0xFF8A8A8E),
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Text(
            text = "${formatScore(score)} / 100",
            color = Color(0xFFF28C28),
            fontSize = 62.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Text(
            text = localizedDrivingModeRu(report.drivingMode),
            color = Color(0xFF8A8A8E),
            fontSize = 24.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        ProgressBar(((score ?: 0.0) / 100.0).toFloat())

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🚗",
                fontSize = 20.sp,
                modifier = Modifier.width(54.dp),
            )

            Text(
                text = scoreBandCurrentRu(score),
                color = Color(0xFFF28C28),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = scoreBandNextRu(score),
                color = Color(0xFF8A8A8E),
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
            )
        }

        SwiftDivider()

        SwiftReportMetricRow("⏱", stringResource(R.string.trip_report_trip_duration), formatDuration(report.tripDurationSec))
        SwiftReportMetricRow("🛣", stringResource(R.string.trip_report_distance), formatKmRu(report.distanceKm))
        SwiftReportMetricRow("◷", stringResource(R.string.trip_report_avg_speed), formatSpeedRu(report.avgSpeedKmh))
        SwiftReportMetricRow("🚗", stringResource(R.string.trip_report_driving_mode), localizedDrivingModeRu(report.drivingMode))

        SwiftDivider()

        SwiftReportMetricRow(
            icon = "🧭",
            label = stringResource(R.string.trip_report_driving_load),
            value = formatNullableOneDecimal(report.drivingLoad),
        )

        SwiftDivider()

        DriverComparisonBlock(
            report = report,
            comparison = comparison,
        )

        Text(
            text = stringResource(R.string.trip_report_events_summary),
            color = Color.Black,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwiftEventSummaryCard(
                icon = "⚠",
                title = stringResource(R.string.trip_report_dangerous_maneuvers_short),
                value = dangerousManeuvers.toString(),
                modifier = Modifier.weight(1f),
            )

            SwiftEventSummaryCard(
                icon = "❄",
                title = stringResource(R.string.trip_report_skid_risk_short),
                value = skidRisk.toString(),
                modifier = Modifier.weight(1f),
            )

            SwiftEventSummaryCard(
                icon = "!",
                title = stringResource(R.string.trip_report_road_anomalies),
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
                    text = stringResource(R.string.trip_report_score_impact),
                    color = Color.Black,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = if (showImpactDetails) "⌃" else "⌄",
                    color = Color.Black,
                    fontSize = 28.sp,
                )
            }
        }

        if (showImpactDetails) {
            SwiftImpactRow("⚠", stringResource(R.string.trip_event_accel_sharp), report.accelSharpTotal)
            SwiftImpactRow("!", stringResource(R.string.trip_event_accel_emergency), report.accelEmergencyTotal)
            SwiftImpactRow("■", stringResource(R.string.trip_event_brake_sharp), report.brakeSharpTotal)
            SwiftImpactRow("●", stringResource(R.string.trip_event_brake_emergency), report.brakeEmergencyTotal)
            SwiftImpactRow("↪", stringResource(R.string.trip_event_turn_sharp), report.turnSharpTotal)
            SwiftImpactRow("↩", stringResource(R.string.trip_event_turn_emergency), report.turnEmergencyTotal)
            SwiftImpactRow("❄", stringResource(R.string.trip_event_accel_in_turn), report.accelInTurnTotal)
            SwiftImpactRow("❄", stringResource(R.string.trip_event_brake_in_turn), report.brakeInTurnTotal)
            SwiftImpactRow("!", stringResource(R.string.trip_event_road_low), report.roadAnomalyLowTotal)
            SwiftImpactRow("!", stringResource(R.string.trip_event_road_high), report.roadAnomalyHighTotal)
        }

        if (showFullDetails) {
            SwiftDivider()

            SwiftGroupedSectionTitle(stringResource(R.string.trip_report_your_trip))
            SwiftGroupedSheet {
                SwiftGroupedRow(stringResource(R.string.trip_report_score), formatNullableOneDecimal(score))
                SwiftGroupedRow(stringResource(R.string.trip_report_driving_load), formatNullableTwoDecimals(report.drivingLoad))
                SwiftGroupedRow(stringResource(R.string.trip_report_driving_mode), localizedDrivingModeRu(report.drivingMode))
            }

            SwiftGroupedSectionTitle(stringResource(R.string.trip_report_totals))
            SwiftGroupedSheet {
                SwiftGroupedRow(stringResource(R.string.trip_report_distance), formatKmRu(report.distanceKm))
                SwiftGroupedRow(stringResource(R.string.trip_report_trip_duration), formatDuration(report.tripDurationSec))
                SwiftGroupedRow(stringResource(R.string.trip_report_avg_speed), formatSpeedRu(report.avgSpeedKmh))
                SwiftGroupedRow(stringResource(R.string.trip_report_driving_mode), localizedDrivingModeRu(report.drivingMode))
                SwiftGroupedRow(stringResource(R.string.trip_report_start), formatReportDateTime(report.clientStartedAt ?: report.receivedStartedAt))
                SwiftGroupedRow(stringResource(R.string.trip_report_finish), formatReportDateTime(report.clientEndedAt ?: report.receivedEndedAt))
            }

            SwiftGroupedSectionTitle(stringResource(R.string.trip_report_events_summary))
            SwiftGroupedSheet {
                SwiftGroupedRow(stringResource(R.string.trip_report_dangerous_maneuvers), dangerousManeuvers.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_skid_risk), skidRisk.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_road_anomalies), roadAnomalies.toString())
            }

            SwiftGroupedSectionTitle(stringResource(R.string.trip_report_stops))
            SwiftGroupedSheet {
                SwiftGroupedSectionTitle(stringResource(R.string.trip_report_stops))
                SwiftGroupedSheet {
                    SwiftGroupedRow(stringResource(R.string.trip_report_stops_count), (report.stopsCount ?: 0).toString())
                    SwiftGroupedRow(stringResource(R.string.trip_report_stops_total_time), stringResource(R.string.trips_common_seconds_format, formatNullableOneDecimal(report.stopsTotalSec)))
                    SwiftGroupedRow(
                        stringResource(R.string.trip_report_stop_duration_p95),
                        stringResource(R.string.trips_common_seconds_format, formatNullableOneDecimal(report.stopsP95Sec))
                    )
                    SwiftGroupedRow(stringResource(R.string.trip_report_stops_per_km), formatNullableThreeDecimals(report.stopsPerKm))
                }
            }

            SwiftGroupedSectionTitle(stringResource(R.string.trip_report_comparison))
            SwiftGroupedSheet {
                SwiftGroupedRow(stringResource(R.string.trip_report_better_than_previous), comparison.betterThanPreviousDriverTrips)
                SwiftGroupedRow(stringResource(R.string.trip_report_better_than_all), comparison.betterThanAllLoadedTrips)
                SwiftGroupedRow(stringResource(R.string.trip_report_previous_driver_trips), comparison.previousDriverTripsCount.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_total_trips_database), comparison.totalComparableTripsCount.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_driver_rank), (report.driverRank ?: 0).toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_total_drivers), (report.totalDrivers ?: 0).toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_driver_avg_score), formatNullableOneDecimal(report.driverAvgScore))
                SwiftGroupedRow(stringResource(R.string.trip_report_driver_trips_total), (report.driverTripsTotal ?: report.prevTripsCount ?: 0).toString())
            }

            SwiftGroupedSectionTitle(stringResource(R.string.trip_report_details))
            SwiftGroupedSheet {
                SwiftGroupedRow(stringResource(R.string.trip_report_batches), report.batchesCount.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_samples), report.samplesCount.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_events), report.eventsCount.toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_stops), (report.stopsCount ?: 0).toString())
                SwiftGroupedRow(stringResource(R.string.trip_report_max_speed), formatSpeedRu(report.speedMaxKmh))
                SwiftGroupedRow(stringResource(R.string.trip_report_p95_speed), formatSpeedRu(report.speedP95Kmh))
            }
        }

        SwiftDivider()

        SwiftOrangeButton(
            text = if (showFullDetails) stringResource(R.string.trips_common_hide) else stringResource(R.string.trips_common_more_details),
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
private fun SwiftReportCompactCaption(text: String) {
    Text(
        text = text,
        color = Color(0xFF8A8A8E),
        fontSize = 15.sp,
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
private fun SwiftGroupedSectionTitle(
    text: String,
) {
    Text(
        text = text,
        color = Color(0xFF8A8A8E),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwiftGroupedSheet(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
private fun SwiftGroupedRow(
    label: String,
    value: String,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = Color.Black,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            )

            Text(
                text = value,
                color = Color(0xFF8A8A8E),
                fontSize = 20.sp,
                textAlign = TextAlign.End,
            )
        }

        HorizontalDivider(
            color = Color(0xFFE5E5EA),
            thickness = 1.dp,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            color = Color(0xFF8A8A8E),
            modifier = Modifier.width(54.dp),
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                color = Color(0xFF8A8A8E),
                fontSize = 20.sp,
                lineHeight = 23.sp,
            )

            Text(
                text = value,
                color = Color.Black,
                fontSize = 24.sp,
                lineHeight = 28.sp,
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
            .height(130.dp)
            .background(Color(0xFFF4F4F5), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = icon,
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = title,
            color = Color(0xFF8A8A8E),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 2,
        )

        Text(
            text = value,
            color = Color.Black,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
@Composable
private fun SwiftImpactRow(
    icon: String,
    label: String,
    value: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            modifier = Modifier.width(38.dp),
            color = Color(0xFF8A8A8E),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = Color(0xFF8A8A8E),
            fontSize = 16.sp,
            maxLines = 1,
        )

        Text(
            text = value.toString(),
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
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
private fun DriverComparisonBlock(
    report: TripReportDto,
    comparison: TripComparisonUi,
) {
    val rank = report.driverRank
    val totalDrivers = report.totalDrivers

    if (rank != null && totalDrivers != null && totalDrivers > 0) {
        Text(
            text = stringResource(R.string.trip_report_driver_rank_format, rank, totalDrivers),
            color = Color.Black,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    SwiftReportMetricRow(
        icon = "🚗",
        label = stringResource(R.string.trip_report_your_avg_rating),
        value = formatNullableOneDecimal(report.driverAvgScore),
    )

    SwiftReportMetricRow(
        icon = "🔑",
        label = stringResource(R.string.trip_report_your_counted_trips),
        value = (report.driverTripsTotal ?: report.prevTripsCount ?: comparison.previousDriverTripsCount).toString(),
    )

    SwiftDivider()

    SwiftReportMetricRow(
        icon = "🏁",
        label = stringResource(R.string.trip_report_better_than_previous),
        value = report.betterThanPrevPct?.let { formatPercentNoDecimal(it) }
            ?: comparison.betterThanPreviousDriverTrips,
    )

    SwiftReportMetricRow(
        icon = "🌍",
        label = stringResource(R.string.trip_report_better_than_all_short),
        value = report.betterThanAllPct?.let { formatPercentNoDecimal(it) }
            ?: comparison.betterThanAllLoadedTrips,
    )

    SwiftReportMetricRow(
        icon = "▥",
        label = stringResource(R.string.trip_report_total_counted_trips),
        value = (report.allTripsCount ?: comparison.totalComparableTripsCount).toString(),
    )

    SwiftDivider()
}

@Composable
private fun ScorePill(score: Double?) {
    Column(
        modifier = Modifier
            .background(
                Color(0xFFFFF4EC),
                RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = formatScore(score),
            color = Color(0xFFF28C28),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "/100",
            color = Color(0xFF8A8A8E),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatNullableOneDecimal(value: Double?): String {
    return value?.let { "%.1f".format(it) } ?: "—"
}

private fun formatPercentNoDecimal(value: Double): String {
    return "${value.roundToInt()}%"
}

@Composable
private fun scoreBandCurrentRu(score: Double?): String {
    val value = score ?: return "—"

    return when {
        value >= 90.0 -> stringResource(R.string.trips_score_band_excellent)
        value >= 75.0 -> stringResource(R.string.trips_score_band_good)
        value >= 60.0 -> stringResource(R.string.trips_score_band_average)
        value >= 40.0 -> stringResource(R.string.trips_score_band_risky)
        else -> stringResource(R.string.trips_score_band_dangerous)
    }
}

private fun formatNullableTwoDecimals(value: Double?): String {
    return value?.let { "%.2f".format(it) } ?: "—"
}

private fun formatNullableThreeDecimals(value: Double?): String {
    return value?.let { "%.3f".format(it) } ?: "—"
}

private fun formatReportDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"

    val cleaned = raw.substringBefore("+").substringBefore("Z")
    val parts = cleaned.split("T")
    if (parts.size != 2) return raw.replace("T", " ").substringBefore(".").take(16)

    val date = parts[0].split("-")
    val time = parts[1].substringBefore(".").split(":")

    if (date.size < 3 || time.size < 2) {
        return raw.replace("T", " ").substringBefore(".").take(16)
    }

    return "${date[2]}.${date[1]}.${date[0]} ${time[0]}:${time[1]}:${time.getOrNull(2) ?: "00"}"
}

@Composable
private fun scoreBandNextRu(score: Double?): String {
    val value = score ?: return ""

    return when {
        value >= 90.0 -> stringResource(R.string.trips_score_band_very_careful)
        value >= 75.0 -> stringResource(R.string.trips_score_band_excellent)
        value >= 60.0 -> stringResource(R.string.trips_score_band_good)
        value >= 40.0 -> stringResource(R.string.trips_score_band_average)
        else -> stringResource(R.string.trips_score_band_risky)
    }
}

private fun reportCoverage(report: TripReportDto?): Float {
    if (report == null) return 0f

    val total = report.batchesCount
    val missing = report.batchesMissingCount ?: 0

    if (total <= 0) return 0f

    return ((total - missing).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun localizedDrivingModeRu(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "mixed" -> stringResource(R.string.trips_driving_mode_mixed)
        "city" -> stringResource(R.string.trips_driving_mode_city)
        "highway" -> stringResource(R.string.trips_driving_mode_highway)
        else -> stringResource(R.string.trips_driving_mode_city)
    }
}

private fun formatScore(score: Double?): String {
    return score?.let { "%.0f".format(it) } ?: "—"
}

@Composable
private fun formatKmRu(value: Double?): String {
    return value?.let { stringResource(R.string.trips_common_km_format, it) } ?: "—"
}

@Composable
private fun formatSpeedRu(value: Double?): String {
    return value?.let { stringResource(R.string.trips_common_kmh_format, it) } ?: "—"
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

@Composable
private fun scoreLabelRu(score: Double?): String {
    if (score == null) return "—"

    return when {
        score >= 80.0 -> stringResource(R.string.trips_score_label_good)
        score >= 60.0 -> stringResource(R.string.trips_score_label_normal)
        else -> stringResource(R.string.trips_score_label_bad)
    }
}

@Composable
private fun scoreHintRu(score: Double?): String {
    if (score == null) return stringResource(R.string.trips_common_no_data)

    return when {
        score >= 80.0 -> stringResource(R.string.trips_score_band_excellent)
        score >= 60.0 -> stringResource(R.string.trips_score_hint_can_improve)
        else -> stringResource(R.string.trips_score_hint_be_more_careful)
    }
}

private data class TripComparisonUi(
    val betterThanPreviousDriverTrips: String,
    val betterThanAllLoadedTrips: String,
    val previousDriverTripsCount: Int,
    val totalComparableTripsCount: Int,
)

private fun calculateTripComparison(
    currentTrip: TripSummaryDto,
    currentScore: Double?,
    archiveTrips: List<TripSummaryDto>,
): TripComparisonUi {
    if (currentScore == null) {
        return TripComparisonUi(
            betterThanPreviousDriverTrips = "—",
            betterThanAllLoadedTrips = "—",
            previousDriverTripsCount = 0,
            totalComparableTripsCount = archiveTrips.count { comparableScore(it) != null },
        )
    }

    val currentSessionId = currentTrip.sessionId
    val currentDriverId = currentTrip.driverId.orEmpty()

    val comparableTrips = archiveTrips
        .filter { comparableScore(it) != null }
        .filterNot { it.sessionId == currentSessionId }

    val previousDriverTrips = comparableTrips.filter {
        it.driverId.orEmpty() == currentDriverId
    }

    val betterThanDriverPercent = percentBetterThan(
        currentScore = currentScore,
        otherScores = previousDriverTrips.mapNotNull { comparableScore(it) },
    )

    val betterThanAllPercent = percentBetterThan(
        currentScore = currentScore,
        otherScores = comparableTrips.mapNotNull { comparableScore(it) },
    )

    return TripComparisonUi(
        betterThanPreviousDriverTrips = formatPercentOrDash(betterThanDriverPercent),
        betterThanAllLoadedTrips = formatPercentOrDash(betterThanAllPercent),
        previousDriverTripsCount = previousDriverTrips.size,
        totalComparableTripsCount = comparableTrips.size + 1,
    )
}

private fun comparableScore(trip: TripSummaryDto): Double? {
    return trip.scoreV2 ?: trip.tripScore
}

private fun percentBetterThan(
    currentScore: Double,
    otherScores: List<Double>,
): Double? {
    if (otherScores.isEmpty()) return null

    val worseCount = otherScores.count { currentScore > it }
    return worseCount.toDouble() / otherScores.size.toDouble() * 100.0
}

private fun formatPercentOrDash(value: Double?): String {
    return value?.let { "%.1f%%".format(it) } ?: "—"
}

private fun formatScoreTwoDecimals(score: Double?): String {
    return score?.let { "%.2f".format(it) } ?: "—"
}

private fun archiveScoreColor(score: Double?): Color {
    return when {
        score == null -> Color(0xFFC7C7CC)
        score >= 60.0 -> Color(0xFF34C759)
        else -> Color(0xFFFF3B30)
    }
}

@Composable
private fun formatArchiveDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"

    val cleaned = raw.substringBefore("+").substringBefore("Z")
    val dateTime = cleaned.split("T")
    if (dateTime.size != 2) {
        return raw.replace("T", " ").substringBefore(".").take(16)
    }

    val dateParts = dateTime[0].split("-")
    val timePart = dateTime[1].substringBefore(".")
    val timeParts = timePart.split(":")

    if (dateParts.size < 3 || timeParts.size < 2) {
        return raw.replace("T", " ").substringBefore(".").take(16)
    }

    val year = dateParts[0]
    val month = dateParts[1].toIntOrNull() ?: return raw.replace("T", " ").substringBefore(".").take(16)
    val day = dateParts[2].toIntOrNull() ?: return raw.replace("T", " ").substringBefore(".").take(16)
    val hour = timeParts[0].toIntOrNull() ?: 0
    val minute = timeParts[1].toIntOrNull() ?: 0

    return "$day ${localizedMonthShort(month)} $year, ${"%02d:%02d".format(hour, minute)}"
}

@Composable
private fun localizedMonthShort(month: Int): String {
    return when (month) {
        1 -> stringResource(R.string.trips_month_short_january)
        2 -> stringResource(R.string.trips_month_short_february)
        3 -> stringResource(R.string.trips_month_short_march)
        4 -> stringResource(R.string.trips_month_short_april)
        5 -> stringResource(R.string.trips_month_short_may)
        6 -> stringResource(R.string.trips_month_short_june)
        7 -> stringResource(R.string.trips_month_short_july)
        8 -> stringResource(R.string.trips_month_short_august)
        9 -> stringResource(R.string.trips_month_short_september)
        10 -> stringResource(R.string.trips_month_short_october)
        11 -> stringResource(R.string.trips_month_short_november)
        12 -> stringResource(R.string.trips_month_short_december)
        else -> ""
    }
}
private fun localizedArchiveError(
    context: Context,
    error: Throwable,
): String {
    val raw = error.message.orEmpty().lowercase()

    return when {
        raw.contains("device is not authorized for this driver_id") ->
            context.getString(R.string.trips_error_device_not_authorized_for_driver)

        raw.contains("unauthorized") || raw.contains("401") ->
            context.getString(R.string.trips_error_session_unauthorized)

        raw.contains("not found") || raw.contains("404") ->
            context.getString(R.string.trips_error_archive_or_report_not_found)

        raw.contains("timeout") ->
            context.getString(R.string.trips_error_server_timeout)

        else ->
            context.getString(R.string.trips_error_loading_format, error.message ?: "unknown")
    }
}