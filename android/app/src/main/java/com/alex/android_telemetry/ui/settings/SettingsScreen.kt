package com.alex.android_telemetry.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

import androidx.compose.foundation.layout.ColumnScope



@Composable
fun SettingsScreen(
    currentDriverId: String?,
    onDone: () -> Unit,
    onOpenDriverAccount: () -> Unit,
    onOpenPermissionsBackground: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDeleteLocalData: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelemetrySwiftColors.ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TelemetrySpacing.ScreenHorizontal)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        SettingsNavigationBar(
            onDone = onDone,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Настройки",
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.LargeTitle,
        )

        Spacer(Modifier.height(26.dp))

        SettingsSectionTitle("Профиль")

        SettingsGroup {
            SettingsRow(
                title = "Водитель",
                value = currentDriverId?.takeIf { it.isNotBlank() } ?: "Не выбран",
                onClick = onOpenDriverAccount,
            )

            SettingsDivider()

            SettingsRow(
                title = "Изменить имя",
                value = null,
                onClick = onOpenDriverAccount,
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsSectionTitle("Приложение")

        SettingsGroup {
            SettingsRow(
                title = "Язык",
                value = "Русский",
                onClick = {},
            )

            SettingsDivider()

            SettingsRow(
                title = "Доступы и фон",
                value = "Проверить",
                onClick = onOpenPermissionsBackground,
            )
        }

        SettingsFootnote(
            text = "Разрешения нужны для корректной записи поездок, фонового мониторинга и восстановления состояния после перезапуска устройства.",
        )

        Spacer(Modifier.height(24.dp))

        SettingsSectionTitle("Система")

        SettingsGroup {
            SettingsRow(
                title = "Диагностика",
                value = "Для разработчика",
                onClick = onOpenDiagnostics,
            )
        }

        SettingsFootnote(
            text = "Диагностика скрыта от основного сценария и используется только для проверки telemetry/runtime слоя.",
        )

        Spacer(Modifier.height(24.dp))

        SettingsSectionTitle("Данные")

        SettingsGroup {
            SettingsDestructiveRow(
                title = "Удалить локальные данные",
                onClick = onDeleteLocalData,
            )

            SettingsDivider()

            SettingsDestructiveRow(
                title = "Удалить аккаунт",
                onClick = onDeleteAccount,
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Android Telemetry",
            modifier = Modifier.fillMaxWidth(),
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Caption,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsNavigationBar(
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDone) {
            Text(
                text = "Готово",
                color = Color(0xFF0A84FF),
                style = TelemetryTypography.BodyEmphasis,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(
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
private fun SettingsGroup(
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
private fun SettingsRow(
    title: String,
    value: String?,
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
            text = title,
            modifier = Modifier.weight(1f),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Body,
        )

        if (value != null) {
            Text(
                text = value,
                color = TelemetrySwiftColors.TextSecondary,
                style = TelemetryTypography.Body,
                textAlign = TextAlign.End,
            )
        }

        Text(
            text = "  ›",
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
        )
    }
}

@Composable
private fun SettingsDestructiveRow(
    title: String,
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
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(0xFFFF3B30),
            style = TelemetryTypography.Body,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TelemetrySwiftColors.Divider),
    )
}

@Composable
private fun SettingsFootnote(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
        color = TelemetrySwiftColors.TextSecondary,
        style = TelemetryTypography.Caption,
    )
}