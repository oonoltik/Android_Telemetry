package com.alex.android_telemetry.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.android_telemetry.ui.design.TelemetrySpacing
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography

@Composable
fun PermissionsBackgroundScreen(
    onBack: () -> Unit,
    onOpenLocationSettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelemetrySwiftColors.ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TelemetrySpacing.ScreenHorizontal)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹ Назад",
                    color = Color(0xFF0A84FF),
                    style = TelemetryTypography.BodyEmphasis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Доступы",
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.LargeTitle,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Настройте разрешения, чтобы поездки записывались стабильно даже после блокировки экрана, перезапуска приложения или перезагрузки телефона.",
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
        )

        Spacer(Modifier.height(24.dp))

        PermissionsHeroCard()

        Spacer(Modifier.height(24.dp))

        PermissionsSectionTitle("Рекомендуется")

        PermissionsGroup {
            PermissionRow(
                icon = "📍",
                title = "Геолокация",
                subtitle = "Нужна для корректного определения поездки и маршрута.",
                status = "Проверить",
                onClick = onOpenLocationSettings,
            )

            PermissionsDivider()

            PermissionRow(
                icon = "🔋",
                title = "Работа в фоне",
                subtitle = "Помогает не терять поездку при выключенном экране.",
                status = "Проверить",
                onClick = onOpenBatterySettings,
            )

            PermissionsDivider()

            PermissionRow(
                icon = "⚙",
                title = "Настройки приложения",
                subtitle = "Откройте системные параметры, если запись работает нестабильно.",
                status = "Открыть",
                onClick = onOpenAppSettings,
            )
        }

        Spacer(Modifier.height(24.dp))

        PermissionsSectionTitle("Почему это важно")

        PermissionsGroup {
            ExplanationRow(
                title = "Replay-safe",
                subtitle = "Если приложение остановится, незавершённая поездка может быть восстановлена.",
            )

            PermissionsDivider()

            ExplanationRow(
                title = "Offline-safe",
                subtitle = "Данные сохраняются локально и отправляются позже.",
            )

            PermissionsDivider()

            ExplanationRow(
                title = "Reboot-safe",
                subtitle = "После перезагрузки устройство может продолжить корректную обработку состояния.",
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0A84FF),
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "Готово",
                style = TelemetryTypography.Headline,
            )
        }
    }
}

@Composable
private fun PermissionsHeroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFEFEFF4))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "🚘",
            style = TelemetryTypography.ScoreHero,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Стабильная запись поездок",
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Title1,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Android может ограничивать фоновые процессы. Эти настройки помогают приложению работать ближе к поведению Swift-сборки.",
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionsSectionTitle(
    text: String,
) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(start = 16.dp, bottom = 7.dp),
        color = TelemetrySwiftColors.TextSecondary,
        style = TelemetryTypography.CaptionEmphasis,
    )
}

@Composable
private fun PermissionsGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TelemetrySwiftColors.CardBackground),
        content = content,
    )
}

@Composable
private fun PermissionRow(
    icon: String,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            modifier = Modifier.padding(end = 12.dp),
            style = TelemetryTypography.Title2,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = TelemetrySwiftColors.TextPrimary,
                style = TelemetryTypography.BodyEmphasis,
            )

            Text(
                text = subtitle,
                color = TelemetrySwiftColors.TextSecondary,
                style = TelemetryTypography.Caption,
            )
        }

        Text(
            text = "$status  ›",
            color = Color(0xFF0A84FF),
            style = TelemetryTypography.Callout,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ExplanationRow(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.BodyEmphasis,
        )

        Text(
            text = subtitle,
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Caption,
        )
    }
}

@Composable
private fun PermissionsDivider() {
    Spacer(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TelemetrySwiftColors.Divider),
    )
}