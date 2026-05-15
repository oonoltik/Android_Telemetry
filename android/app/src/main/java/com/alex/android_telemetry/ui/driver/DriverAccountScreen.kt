package com.alex.android_telemetry.ui.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.launch

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
    var driverIdInput by remember {
        mutableStateOf(driverRepository.getCurrentDriverId().orEmpty())
    }
    var passwordInput by remember { mutableStateOf("") }

    var prepareStatus by remember { mutableStateOf("-") }
    var registerStatus by remember { mutableStateOf("-") }
    var loginStatus by remember { mutableStateOf("-") }
    var deleteStatus by remember { mutableStateOf("-") }

    var busy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val currentDriverId = driverRepository.getCurrentDriverId()
    val tripActive = state.telemetryMode != TelemetryMode.IDLE

    fun clearStatuses() {
        prepareStatus = "-"
        registerStatus = "-"
        loginStatus = "-"
        deleteStatus = "-"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Driver / Account",
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onBack) {
                Text("Назад")
            }
        }

        AccountStateCard(
            deviceId = deviceId,
            currentDriverId = currentDriverId,
            tripActive = tripActive,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Credentials",
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = driverIdInput,
                    onValueChange = {
                        driverIdInput = it
                        clearStatuses()
                    },
                    label = { Text("driver_id") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        clearStatuses()
                    },
                    label = { Text("password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        DriverActionsCard(
            busy = busy,
            prepareStatus = prepareStatus,
            registerStatus = registerStatus,
            loginStatus = loginStatus,
            deleteStatus = deleteStatus,
            onPrepare = {
                scope.launch {
                    val driverId = driverIdInput.trim()
                    if (driverId.isEmpty()) {
                        prepareStatus = "driver_id is empty"
                        return@launch
                    }

                    busy = true
                    prepareStatus = "preparing…"

                    prepareStatus = when (
                        val result = driverPrepareManager.prepare(
                            deviceId = deviceId,
                            driverId = driverId,
                        )
                    ) {
                        is DriverPrepareResult.Success -> "ok: ${result.status}"
                        is DriverPrepareResult.Failed -> "error: ${result.message ?: "unknown"}"
                    }

                    busy = false
                }
            },
            onRegister = {
                scope.launch {
                    val driverId = driverIdInput.trim()
                    val password = passwordInput

                    if (driverId.isEmpty()) {
                        registerStatus = "driver_id is empty"
                        return@launch
                    }

                    if (password.isEmpty()) {
                        registerStatus = "password is empty"
                        return@launch
                    }

                    busy = true
                    registerStatus = "registering…"

                    registerStatus = when (
                        val result = driverRegisterManager.register(
                            deviceId = deviceId,
                            driverId = driverId,
                            password = password,
                        )
                    ) {
                        is DriverRegisterResult.Success -> "ok: ${result.status}"
                        is DriverRegisterResult.Failed -> "error: ${result.message ?: "unknown"}"
                    }

                    busy = false
                }
            },
            onLogin = {
                scope.launch {
                    val driverId = driverIdInput.trim()
                    val password = passwordInput

                    if (driverId.isEmpty()) {
                        loginStatus = "driver_id is empty"
                        return@launch
                    }

                    if (password.isEmpty()) {
                        loginStatus = "password is empty"
                        return@launch
                    }

                    busy = true
                    loginStatus = if (tripActive) {
                        "stopping active trip before login…"
                    } else {
                        "logging in…"
                    }

                    if (tripActive) {
                        telemetryFacade.stopTrip()
                    }

                    loginStatus = when (
                        val result = driverLoginManager.login(
                            deviceId = deviceId,
                            driverId = driverId,
                            password = password,
                        )
                    ) {
                        is DriverLoginResult.Success -> "ok: ${result.status}"
                        is DriverLoginResult.Failed -> "error: ${result.message ?: "unknown"}"
                    }

                    busy = false
                }
            },
            onDelete = {
                scope.launch {
                    val driverId = driverIdInput.trim()

                    if (driverId.isEmpty()) {
                        deleteStatus = "driver_id is empty"
                        return@launch
                    }

                    busy = true
                    deleteStatus = if (tripActive) {
                        "stopping active trip before delete…"
                    } else {
                        "deleting account…"
                    }

                    if (tripActive) {
                        telemetryFacade.stopTrip()
                    }

                    deleteStatus = when (
                        val result = accountDeleteManager.delete(
                            deviceId = deviceId,
                            driverId = driverId,
                        )
                    ) {
                        is AccountDeleteResult.Success -> {
                            driverIdInput = ""
                            passwordInput = ""
                            "ok: ${result.status}"
                        }

                        is AccountDeleteResult.Failed -> "error: ${result.message ?: "unknown"}"
                    }

                    busy = false
                }
            },
        )
    }
}

@Composable
private fun AccountStateCard(
    deviceId: String,
    currentDriverId: String?,
    tripActive: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Current state",
                style = MaterialTheme.typography.titleMedium,
            )

            Text("deviceId: $deviceId")
            Text("currentDriverId: ${currentDriverId ?: "-"}")
            Text("tripActive: $tripActive")

            if (tripActive) {
                Text("Login/delete остановит активную поездку перед сменой аккаунта.")
            }
        }
    }
}

@Composable
private fun DriverActionsCard(
    busy: Boolean,
    prepareStatus: String,
    registerStatus: String,
    loginStatus: String,
    deleteStatus: String,
    onPrepare: () -> Unit,
    onRegister: () -> Unit,
    onLogin: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
            )

            Button(
                enabled = !busy,
                onClick = onPrepare,
            ) {
                Text("Prepare driver")
            }
            Text("prepareStatus: $prepareStatus")

            Button(
                enabled = !busy,
                onClick = onRegister,
            ) {
                Text("Register driver")
            }
            Text("registerStatus: $registerStatus")

            Button(
                enabled = !busy,
                onClick = onLogin,
            ) {
                Text("Login driver")
            }
            Text("loginStatus: $loginStatus")

            HorizontalDivider()

            OutlinedButton(
                enabled = !busy,
                onClick = onDelete,
            ) {
                Text("Delete account")
            }
            Text("deleteStatus: $deleteStatus")
        }
    }
}