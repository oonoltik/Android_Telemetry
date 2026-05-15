package com.alex.android_telemetry.ui.home

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.ui.status.RuntimeWarningBanner

@Composable
fun TelemetryHomeScreen(
    state: TripRuntimeState,
    currentDriverId: String?,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onOpenTripsArchive: () -> Unit,
    onOpenDriverAccount: () -> Unit,
    onOpenPermissionsBackground: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val isTripActive = state.telemetryMode != TelemetryMode.IDLE
    val statusText = when {
        isTripActive -> "Поездка активна"
        state.dayMonitoringEnabled -> "Мониторинг"
        else -> "Готово"
    }

    val statusColor = when {
        isTripActive -> Color(0xFF3B82F6)
        state.dayMonitoringEnabled -> Color(0xFFF28C28)
        else -> Color(0xFF5AC85A)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F3F6))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HomeTopBar(
            onOpenSettings = onOpenPermissionsBackground,
        )

        DrivingScoreHero(
            score = 0,
            subtitle = if (currentDriverId.isNullOrBlank()) {
                "Водитель не выбран"
            } else {
                "Водитель подключён"
            },
        )

        StatusPill(
            text = statusText,
            color = statusColor,
        )

        RuntimeWarningBanner(
            state = state,
            currentDriverId = currentDriverId,
        )

        MainActionsRow(
            isTripActive = isTripActive,
            onStartTrip = onStartTrip,
            onStopTrip = onStopTrip,
        )

        SecondaryActions(
            onOpenTripsArchive = onOpenTripsArchive,
            onOpenDriverAccount = onOpenDriverAccount,
            onOpenPermissionsBackground = onOpenPermissionsBackground,
        )

        ModeSegment()

        GameButton()

        TextButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Диагностика",
                color = Color(0xFF8A8A8E),
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Настройки",
                color = Color.Black,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun DrivingScoreHero(
    score: Int,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFEFF3), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Оценка вождения",
            color = Color.Black,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "$score / 100",
            color = Color(0xFF3B82F6),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = subtitle,
            color = Color(0xFF8A8A8E),
            fontSize = 20.sp,
        )

        Text(
            text = "↘ - за последние 5 поездок",
            color = Color(0xFFF28C28),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )

        ThinDivider()

        Text(
            text = "Топ 44% водителей",
            color = Color.Black,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "До уровня Спокойный водитель осталось 0.7",
            color = Color(0xFF8A8A8E),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(Color(0xFF5AC85A))
            Dot(Color(0xFFFF4D4D))
            Dot(Color(0xFFFF4D4D))
            Dot(Color(0xFFFF4D4D))
            Dot(Color(0xFFFF4D4D))
        }

        Text(
            text = "Есть поездка, которую можно улучшить",
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Сохраните зелёную серию в следующей поездке",
            color = Color(0xFF8A8A8E),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFEFF3), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(14.dp)
                .background(color, RoundedCornerShape(999.dp)),
        )

        Spacer(Modifier.width(14.dp))

        Text(
            text = text,
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MainActionsRow(
    isTripActive: Boolean,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Button(
            onClick = onStartTrip,
            enabled = !isTripActive,
            modifier = Modifier
                .weight(1f)
                .height(74.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                disabledContainerColor = Color(0xFFE4E4E7),
                contentColor = Color.White,
                disabledContentColor = Color(0xFFB8B8BE),
            ),
            shape = RoundedCornerShape(34.dp),
        ) {
            Text(
                text = "Старт",
                fontSize = 22.sp,
            )
        }

        Button(
            onClick = onStopTrip,
            enabled = isTripActive,
            modifier = Modifier
                .weight(1f)
                .height(74.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                disabledContainerColor = Color(0xFFE4E4E7),
                contentColor = Color.White,
                disabledContentColor = Color(0xFFB8B8BE),
            ),
            shape = RoundedCornerShape(34.dp),
        ) {
            Text(
                text = "Стоп",
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun SecondaryActions(
    onOpenTripsArchive: () -> Unit,
    onOpenDriverAccount: () -> Unit,
    onOpenPermissionsBackground: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomePillButton(
            text = "↺ Архив поездок",
            modifier = Modifier.weight(1f),
            onClick = onOpenTripsArchive,
        )

        HomePillButton(
            text = "🚘 Водитель",
            modifier = Modifier.weight(1f),
            onClick = onOpenDriverAccount,
        )
    }

    HomePillButton(
        text = "⚙ Настройки доступа",
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenPermissionsBackground,
    )
}

@Composable
private fun ModeSegment() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE4E4E7), RoundedCornerShape(22.dp))
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Дорога",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
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
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun GameButton() {
    HomePillButton(
        text = "≋ Игра Спаси Рыбку",
        modifier = Modifier.fillMaxWidth(),
        onClick = {},
    )
}

@Composable
private fun HomePillButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE4E4E7),
            contentColor = Color(0xFF3B82F6),
        ),
        shape = RoundedCornerShape(32.dp),
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(18.dp)
            .background(color, RoundedCornerShape(999.dp)),
    )
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFD8D8DD)),
    )
}