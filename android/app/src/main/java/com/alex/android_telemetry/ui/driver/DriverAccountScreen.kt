package com.alex.android_telemetry.ui.driver

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.telemetry.driver.AccountDeleteManager
import com.alex.android_telemetry.telemetry.driver.AccountDeleteResult
import com.alex.android_telemetry.telemetry.driver.DriverLoginManager
import com.alex.android_telemetry.telemetry.driver.DriverLoginResult
import com.alex.android_telemetry.telemetry.driver.DriverPrepareManager
import com.alex.android_telemetry.telemetry.driver.DriverPrepareResult
import com.alex.android_telemetry.telemetry.driver.DriverRegisterManager
import com.alex.android_telemetry.telemetry.driver.DriverRegisterResult
import com.alex.android_telemetry.telemetry.driver.DriverRepository
import com.alex.android_telemetry.telemetry.runtime.TelemetryFacade
import com.alex.android_telemetry.ui.design.TelemetrySpacing
import com.alex.android_telemetry.ui.design.TelemetrySwiftColors
import com.alex.android_telemetry.ui.design.TelemetryTypography
import kotlinx.coroutines.launch

private sealed class DriverSetupStage {
    data object EnterId : DriverSetupStage()
    data class NeedPassword(val isNew: Boolean) : DriverSetupStage()
    data object Working : DriverSetupStage()
}

@Composable
fun DriverAccountScreen(
    state: TripRuntimeState,
    deviceId: String,
    driverRepository: DriverRepository,
    driverPrepareManager: DriverPrepareManager,
    driverRegisterManager: DriverRegisterManager,
    driverLoginManager: DriverLoginManager,
    accountDeleteManager: AccountDeleteManager,
    telemetryFacade: TelemetryFacade,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf<DriverSetupStage>(DriverSetupStage.EnterId) }
    var driverId by remember { mutableStateOf(driverRepository.getCurrentDriverId().orEmpty()) }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var deleteStatus by remember { mutableStateOf<String?>(null) }

    val currentDriverId = driverRepository.getCurrentDriverId().orEmpty()
    val isTripActive = state.telemetryMode != TelemetryMode.IDLE

    fun trimmedDriverId(): String = driverId.trim()
    fun trimmedPassword(): String = password.trim()

    fun resetToDriverId() {
        errorText = null
        password = ""
        stage = DriverSetupStage.EnterId
    }

    fun handleKnownDevice(knownDriverId: String) {
        driverRepository.setCurrentDriverId(knownDriverId)
        onBack()
    }

    fun prepareDriver() {
        val id = trimmedDriverId()
        if (id.isEmpty()) return

        errorText = null
        deleteStatus = null
        stage = DriverSetupStage.Working

        scope.launch {
            when (val result = driverPrepareManager.prepare(deviceId = deviceId, driverId = id)) {
                is DriverPrepareResult.Success -> {
                    when (normalizeDriverStatus(result.status)) {
                        "known_device" -> handleKnownDevice(id)
                        "need_password" -> {
                            password = ""
                            stage = DriverSetupStage.NeedPassword(isNew = false)
                        }
                        "new_driver" -> {
                            password = ""
                            stage = DriverSetupStage.NeedPassword(isNew = true)
                        }
                        else -> {
                            stage = DriverSetupStage.EnterId
                            errorText = "Неизвестный ответ сервера: ${result.status}"
                        }
                    }
                }

                is DriverPrepareResult.Failed -> {
                    stage = DriverSetupStage.EnterId
                    errorText = localizedDriverAuthError(result.message)
                }
            }
        }
    }

    fun submitPassword(isNew: Boolean) {
        val id = trimmedDriverId()
        val pw = trimmedPassword()
        if (id.isEmpty() || pw.isEmpty()) return

        errorText = null
        deleteStatus = null
        stage = DriverSetupStage.Working

        scope.launch {
            val authSuccess = if (isNew) {
                when (val result = driverRegisterManager.register(deviceId = deviceId, driverId = id, password = pw)) {
                    is DriverRegisterResult.Success -> true
                    is DriverRegisterResult.Failed -> {
                        errorText = localizedDriverAuthError(result.message)
                        false
                    }
                }
            } else {
                when (val result = driverLoginManager.login(deviceId = deviceId, driverId = id, password = pw)) {
                    is DriverLoginResult.Success -> true
                    is DriverLoginResult.Failed -> {
                        errorText = localizedDriverAuthError(result.message)
                        false
                    }
                }
            }

            if (!authSuccess) {
                stage = DriverSetupStage.EnterId
                return@launch
            }

            when (val check = driverPrepareManager.prepare(deviceId = deviceId, driverId = id)) {
                is DriverPrepareResult.Success -> {
                    when (normalizeDriverStatus(check.status)) {
                        "known_device" -> handleKnownDevice(id)
                        "need_password" -> {
                            stage = DriverSetupStage.NeedPassword(isNew = false)
                            errorText = "Сервер всё ещё требует пароль."
                        }
                        "new_driver" -> {
                            stage = DriverSetupStage.NeedPassword(isNew = true)
                            errorText = "Водитель не был создан на сервере."
                        }
                        else -> {
                            stage = DriverSetupStage.EnterId
                            errorText = "Неизвестный ответ сервера: ${check.status}"
                        }
                    }
                }

                is DriverPrepareResult.Failed -> {
                    stage = DriverSetupStage.EnterId
                    errorText = localizedDriverAuthError(check.message)
                }
            }
        }
    }

    fun deleteAccount() {
        val id = currentDriverId.ifBlank { trimmedDriverId() }
        if (id.isBlank()) return

        errorText = null
        deleteStatus = null
        stage = DriverSetupStage.Working

        scope.launch {
            when (val result = accountDeleteManager.delete(deviceId = deviceId, driverId = id)) {
                is AccountDeleteResult.Success -> {
                    driverId = ""
                    password = ""
                    deleteStatus = "Аккаунт удалён"
                    stage = DriverSetupStage.EnterId
                }

                is AccountDeleteResult.Failed -> {
                    errorText = localizedDriverAuthError(result.message)
                    stage = DriverSetupStage.EnterId
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TelemetrySwiftColors.ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TelemetrySpacing.ScreenHorizontal)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        DriverNavigationBar(
            onBack = onBack,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Водитель",
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.LargeTitle,
        )

        Spacer(Modifier.height(24.dp))

        DriverHeroCard(
            driverName = currentDriverId.ifBlank { "Водитель не выбран" },
            subtitle = when {
                isTripActive -> "Во время активной поездки менять водителя не рекомендуется."
                currentDriverId.isNotBlank() -> "Профиль используется для привязки поездок и расчёта персональной статистики."
                else -> "Введите driver ID. Если водитель уже существует, потребуется пароль. Если нет — будет создан новый профиль."
            },
        )

        Spacer(Modifier.height(24.dp))

        DriverSectionTitle("Driver ID")

        DriverGroup {
            DriverIdInputRow(
                value = driverId,
                enabled = stage !is DriverSetupStage.Working,
                onValueChange = {
                    driverId = it
                    errorText = null
                    if (stage is DriverSetupStage.NeedPassword) {
                        password = ""
                        stage = DriverSetupStage.EnterId
                    }
                },
            )

            if (stage is DriverSetupStage.NeedPassword) {
                DriverDivider()

                PasswordInputRow(
                    value = password,
                    enabled = true,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                )
            }
        }

        DriverFootnote(
            text = when (val currentStage = stage) {
                DriverSetupStage.EnterId -> "Нажмите «Продолжить», чтобы проверить driver ID на сервере."
                is DriverSetupStage.NeedPassword -> {
                    if (currentStage.isNew) {
                        "Такого водителя ещё нет. Введите пароль, чтобы создать профиль."
                    } else {
                        "Этот водитель уже существует. Введите пароль, чтобы войти."
                    }
                }
                DriverSetupStage.Working -> "Проверяем данные…"
            },
        )

        if (errorText != null) {
            Spacer(Modifier.height(12.dp))
            DriverErrorText(errorText.orEmpty())
        }

        if (deleteStatus != null) {
            Spacer(Modifier.height(12.dp))
            DriverSuccessText(deleteStatus.orEmpty())
        }

        Spacer(Modifier.height(24.dp))

        DriverPrimaryAction(
            stage = stage,
            driverId = trimmedDriverId(),
            password = trimmedPassword(),
            onContinue = { prepareDriver() },
            onSubmitPassword = { isNew -> submitPassword(isNew) },
            onBackToDriverId = { resetToDriverId() },
        )

        Spacer(Modifier.height(24.dp))

        DriverSectionTitle("Состояние")

        DriverGroup {
            DriverInfoRow(
                title = "Активный водитель",
                value = currentDriverId.ifBlank { "Не выбран" },
            )

            DriverDivider()

            DriverInfoRow(
                title = "Устройство",
                value = deviceId,
            )

            DriverDivider()

            DriverInfoRow(
                title = "Запись поездок",
                value = if (currentDriverId.isNotBlank()) "Готова" else "Нужен водитель",
            )
        }

        Spacer(Modifier.height(24.dp))

        DriverSectionTitle("Аккаунт")

        DriverGroup {
            DriverDestructiveRow(
                title = "Удалить аккаунт",
                enabled = currentDriverId.isNotBlank() && stage !is DriverSetupStage.Working,
                onClick = { deleteAccount() },
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Driver setup",
            modifier = Modifier.fillMaxWidth(),
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Caption,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DriverNavigationBar(
    onBack: () -> Unit,
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
}

@Composable
private fun DriverHeroCard(
    driverName: String,
    subtitle: String,
) {
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
            text = driverName,
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Title1,
            textAlign = TextAlign.Center,
        )

        Text(
            text = subtitle,
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DriverSectionTitle(
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
private fun DriverGroup(
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
private fun DriverIdInputRow(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Driver ID",
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.BodyEmphasis,
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Например: alex",
                    color = TelemetrySwiftColors.TextSecondary,
                    style = TelemetryTypography.Body,
                )
            },
            textStyle = TelemetryTypography.Body,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0A84FF),
                unfocusedBorderColor = TelemetrySwiftColors.Divider,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
            ),
        )
    }
}

@Composable
private fun PasswordInputRow(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Пароль",
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.BodyEmphasis,
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = {
                Text(
                    text = "Введите пароль",
                    color = TelemetrySwiftColors.TextSecondary,
                    style = TelemetryTypography.Body,
                )
            },
            textStyle = TelemetryTypography.Body,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0A84FF),
                unfocusedBorderColor = TelemetrySwiftColors.Divider,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
    }
}

@Composable
private fun DriverPrimaryAction(
    stage: DriverSetupStage,
    driverId: String,
    password: String,
    onContinue: () -> Unit,
    onSubmitPassword: (Boolean) -> Unit,
    onBackToDriverId: () -> Unit,
) {
    when (stage) {
        DriverSetupStage.EnterId -> {
            DriverBlueButton(
                text = "Продолжить",
                enabled = driverId.isNotBlank(),
                onClick = onContinue,
            )
        }

        is DriverSetupStage.NeedPassword -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DriverBlueButton(
                    text = if (stage.isNew) "Создать" else "Войти",
                    enabled = driverId.isNotBlank() && password.isNotBlank(),
                    onClick = { onSubmitPassword(stage.isNew) },
                )

                DriverSecondaryButton(
                    text = "Назад",
                    onClick = onBackToDriverId,
                )
            }
        }

        DriverSetupStage.Working -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(Color(0xFFE5E5EA)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    color = Color(0xFF0A84FF),
                    strokeWidth = 2.dp,
                )

                Text(
                    text = "Проверяем…",
                    color = TelemetrySwiftColors.TextPrimary,
                    style = TelemetryTypography.Headline,
                )
            }
        }
    }
}

@Composable
private fun DriverBlueButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(29.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0A84FF),
            disabledContainerColor = Color(0xFFE5E5EA),
            contentColor = Color.White,
            disabledContentColor = Color(0xFF8A8A8E),
        ),
    ) {
        Text(
            text = text,
            style = TelemetryTypography.Headline,
        )
    }
}

@Composable
private fun DriverSecondaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE5E5EA),
            contentColor = Color(0xFF0A84FF),
        ),
    ) {
        Text(
            text = text,
            style = TelemetryTypography.Headline,
        )
    }
}

@Composable
private fun DriverInfoRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = TelemetrySwiftColors.TextPrimary,
            style = TelemetryTypography.Body,
        )

        Text(
            text = value,
            color = TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DriverDestructiveRow(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (enabled) Color(0xFFFF3B30) else TelemetrySwiftColors.TextSecondary,
            style = TelemetryTypography.Body,
        )
    }
}

@Composable
private fun DriverDivider() {
    Spacer(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(TelemetrySwiftColors.Divider),
    )
}

@Composable
private fun DriverFootnote(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
        color = TelemetrySwiftColors.TextSecondary,
        style = TelemetryTypography.Caption,
    )
}

@Composable
private fun DriverErrorText(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFE5E5))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = Color(0xFFFF3B30),
        style = TelemetryTypography.CaptionEmphasis,
    )
}

@Composable
private fun DriverSuccessText(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE6F7EA))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = Color(0xFF248A3D),
        style = TelemetryTypography.CaptionEmphasis,
    )
}

private fun normalizeDriverStatus(status: String): String {
    return status
        .trim()
        .replace("-", "_")
        .replace(" ", "_")
        .let { raw ->
            raw.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        }
        .lowercase()
}

private fun localizedDriverAuthError(message: String?): String {
    val raw = message.orEmpty().lowercase()

    return when {
        raw.contains("driver_id not found") || raw.contains("not found") -> {
            "Driver ID не найден."
        }

        raw.contains("invalid password") || raw.contains("wrong password") -> {
            "Неверный пароль."
        }

        raw.contains("device confirmation failed") -> {
            "Не удалось подтвердить устройство."
        }

        raw.contains("device is not authorized for this driver_id") -> {
            "Это устройство не авторизовано для выбранного driver ID."
        }

        raw.contains("temporarily unavailable") ||
                raw.contains("unreachable") ||
                raw.contains("timed out") ||
                raw.contains("timeout") -> {
            "Сервис авторизации временно недоступен. Попробуйте ещё раз."
        }

        raw.isBlank() -> {
            "Не удалось выполнить вход. Попробуйте ещё раз."
        }

        else -> {
            "Не удалось выполнить вход. Попробуйте ещё раз."
        }
    }
}