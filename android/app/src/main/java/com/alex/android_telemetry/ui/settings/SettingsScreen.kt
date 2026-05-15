package com.alex.android_telemetry.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(Color(0xFFF1F1F4))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SettingsTopBar(onDone = onDone)

        SettingsSectionTitle("Язык")
        SettingsCard {
            SettingsRow(
                title = "Язык",
                value = "Русский⌄",
            )
        }

        SettingsSectionTitle("Имя пользователя")
        SettingsCard {
            SettingsRow(
                title = "Текущий",
                value = currentDriverId?.takeIf { it.isNotBlank() } ?: "—",
            )

            SettingsDivider()

            SettingsBlueAction(
                text = "Изменить имя пользователя",
                onClick = onOpenDriverAccount,
            )
        }

        SettingsSectionTitle("Конфиденциальность")
        SettingsCard {
            Text(
                text = "В данной версии приложения технические идентификаторы скрыты из публичного интерфейса. Вы можете удалить локально сохранённые данные приложения на этом устройстве.",
                color = Color(0xFF8A8A8E),
                fontSize = 18.sp,
                lineHeight = 25.sp,
            )

            SettingsDivider()

            SettingsBlueAction(
                text = "Политика конфиденциальности",
                onClick = {},
            )

            SettingsDivider()

            SettingsBlueAction(
                text = "Условия использования",
                onClick = {},
            )

            SettingsDivider()

            SettingsRedAction(
                text = "Удалить локальные данные",
                onClick = onDeleteLocalData,
            )
        }

        SettingsSectionTitle("Аккаунт")
        SettingsCard {
            SettingsRedAction(
                text = "Удалить аккаунт",
                onClick = onDeleteAccount,
            )
        }

        SettingsSectionTitle("Локация (фон)")
        SettingsCaption(
            "Для стабильной работы GPS в фоне Android требует разрешение Always/Background location, уведомления и отключение ограничений батареи."
        )

        SettingsCard {
            SettingsBlueAction(
                text = "Проверить доступы и фоновые ограничения",
                onClick = onOpenPermissionsBackground,
            )
        }

        SettingsSectionTitle("Диагностика")
        SettingsCard {
            SettingsBlueAction(
                text = "Открыть диагностику телематики",
                onClick = onOpenDiagnostics,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsTopBar(
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Настройки",
            color = Color.Black,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
        )

        TextButton(
            onClick = onDone,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Готово",
                color = Color.Black,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    text: String,
) {
    Text(
        text = text,
        color = Color(0xFF8A8A8E),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, bottom = 0.dp),
    )
}

@Composable
private fun SettingsCaption(
    text: String,
) {
    Text(
        text = text,
        color = Color(0xFF8A8A8E),
        fontSize = 18.sp,
        lineHeight = 25.sp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.Black,
            fontSize = 22.sp,
        )

        Text(
            text = value,
            color = Color(0xFF8A8A8E),
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun SettingsBlueAction(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = Color(0xFF3B82F6),
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsRedAction(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = Color(0xFFFF3B30),
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color(0xFFE0E0E0),
        thickness = 1.dp,
    )
}