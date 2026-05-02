package com.alex.android_telemetry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode
import com.alex.android_telemetry.telemetry.domain.model.TelemetryMode
import com.alex.android_telemetry.telemetry.domain.model.TripRuntimeState
import com.alex.android_telemetry.telemetry.driver.AccountDeleteResult
import com.alex.android_telemetry.telemetry.driver.DriverLoginResult
import com.alex.android_telemetry.telemetry.driver.DriverPrepareResult
import com.alex.android_telemetry.telemetry.driver.DriverRegisterResult
import com.alex.android_telemetry.telemetry.service.TelemetryServiceStarter
import com.alex.android_telemetry.ui.theme.Android_TelemetryTheme
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {

    private lateinit var graph: TelemetryAppGraph

    private val activityRecognitionPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d("UI", "ACTIVITY_RECOGNITION granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestActivityRecognitionPermissionIfNeeded()

        graph = TelemetryAppGraph.get(applicationContext)
        val serviceStarter = TelemetryServiceStarter(applicationContext)

        setContent {
            Android_TelemetryTheme {
                val scope = rememberCoroutineScope()
                val state by graph.facade.observeState().collectAsState()



                var driverIdInput by remember {
                    mutableStateOf(graph.driverRepository.getCurrentDriverId().orEmpty())
                }
                var passwordInput by remember { mutableStateOf("") }
                var prepareStatus by remember { mutableStateOf("-") }
                var registerStatus by remember { mutableStateOf("-") }
                var loginStatus by remember { mutableStateOf("-") }
                var deleteStatus by remember { mutableStateOf("-") }

                DebugTelemetryScreen(
                    state = state,

                    driverIdInput = driverIdInput,
                    passwordInput = passwordInput,
                    prepareStatus = prepareStatus,
                    registerStatus = registerStatus,
                    loginStatus = loginStatus,
                    deleteStatus = deleteStatus,
                    onDriverIdChanged = { driverIdInput = it },
                    onPasswordChanged = { passwordInput = it },
                    onPrepareDriver = {
                        scope.launch {
                            try {
                                val driverId = driverIdInput.trim()
                                if (driverId.isEmpty()) {
                                    prepareStatus = "driver_id is empty"
                                    return@launch
                                }

                                when (val result = graph.driverPrepareManager.prepare(
                                    deviceId = graph.deviceIdProvider.get(),
                                    driverId = driverId,
                                )) {
                                    is DriverPrepareResult.Success -> {
                                        prepareStatus = "ok: ${result.status}"
                                        Log.d(
                                            "DriverPrepare",
                                            "prepare success driverId=${result.driverId} status=${result.status}"
                                        )
                                    }

                                    is DriverPrepareResult.Failed -> {
                                        prepareStatus = "error: ${result.message ?: "unknown"}"
                                        Log.e("DriverPrepare", "prepare failed", result.error)
                                    }
                                }
                            } catch (t: Throwable) {
                                prepareStatus = "error: ${t.message ?: "unknown"}"
                                Log.e("DriverPrepare", "prepare crashed", t)
                            }
                        }
                    },
                    onRegisterDriver = {
                        scope.launch {
                            try {
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

                                when (val result = graph.driverRegisterManager.register(
                                    deviceId = graph.deviceIdProvider.get(),
                                    driverId = driverId,
                                    password = password,
                                )) {
                                    is DriverRegisterResult.Success -> {
                                        registerStatus = "ok: ${result.status}"
                                        Log.d(
                                            "DriverRegister",
                                            "register success driverId=${result.driverId} status=${result.status}"
                                        )
                                    }

                                    is DriverRegisterResult.Failed -> {
                                        registerStatus = "error: ${result.message ?: "unknown"}"
                                        Log.e("DriverRegister", "register failed", result.error)
                                    }
                                }
                            } catch (t: Throwable) {
                                registerStatus = "error: ${t.message ?: "unknown"}"
                                Log.e("DriverRegister", "register crashed", t)
                            }
                        }
                    },
                    onLoginDriver = {
                        scope.launch {
                            try {
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

                                val tripActive = state.telemetryMode != TelemetryMode.IDLE
                                if (tripActive) {
                                    Log.d("DriverLogin", "active trip detected, stopping before login")
                                    graph.facade.stopTrip()
                                }

                                when (val result = graph.driverLoginManager.login(
                                    deviceId = graph.deviceIdProvider.get(),
                                    driverId = driverId,
                                    password = password,
                                )) {
                                    is DriverLoginResult.Success -> {
                                        loginStatus = "ok: ${result.status}"
                                        Log.d(
                                            "DriverLogin",
                                            "login success driverId=${result.driverId} status=${result.status}"
                                        )
                                    }

                                    is DriverLoginResult.Failed -> {
                                        loginStatus = "error: ${result.message ?: "unknown"}"
                                        Log.e("DriverLogin", "login failed", result.error)
                                    }
                                }
                            } catch (t: Throwable) {
                                loginStatus = "error: ${t.message ?: "unknown"}"
                                Log.e("DriverLogin", "login crashed", t)
                            }
                        }
                    },
                    onDeleteAccount = {
                        scope.launch {
                            try {
                                val driverId = driverIdInput.trim()

                                if (driverId.isEmpty()) {
                                    deleteStatus = "driver_id is empty"
                                    return@launch
                                }

                                val tripActive = state.telemetryMode != TelemetryMode.IDLE
                                if (tripActive) {
                                    Log.d("AccountDelete", "active trip detected, stopping before delete")
                                    graph.facade.stopTrip()
                                }

                                when (val result = graph.accountDeleteManager.delete(
                                    deviceId = graph.deviceIdProvider.get(),
                                    driverId = driverId,
                                )) {
                                    is AccountDeleteResult.Success -> {
                                        deleteStatus = "ok: ${result.status}"
                                        driverIdInput = ""
                                        passwordInput = ""
                                        prepareStatus = "-"
                                        registerStatus = "-"
                                        loginStatus = "-"
                                        Log.d(
                                            "AccountDelete",
                                            "delete success driverId=${result.driverId} status=${result.status}"
                                        )
                                    }

                                    is AccountDeleteResult.Failed -> {
                                        deleteStatus = "error: ${result.message ?: "unknown"}"
                                        Log.e("AccountDelete", "delete failed", result.error)
                                    }
                                }
                            } catch (t: Throwable) {
                                deleteStatus = "error: ${t.message ?: "unknown"}"
                                Log.e("AccountDelete", "delete crashed", t)
                            }
                        }
                    },
                    onStartSingleTrip = {
                        Log.d("UI", "SINGLE TRIP BUTTON CLICKED")
                        scope.launch {
                            try {
                                val driverId = driverIdInput.trim().ifEmpty { null }
                                serviceStarter.startTrip(
                                    deviceId = graph.deviceIdProvider.get(),
                                    driverId = driverId,
                                    trackingMode = TrackingMode.SINGLE_TRIP,
                                    transportMode = TransportMode.UNKNOWN,
                                )
                                Log.d("UI", "single trip start invoked")
                            } catch (t: Throwable) {
                                Log.e("UI", "single trip start FAILED", t)
                            }
                        }
                    },
                    onEnableMdMonitoring = {
                        Log.d("UI", "MD MONITORING BUTTON CLICKED")
                        scope.launch {
                            try {
                                serviceStarter.enableDayMonitoring()

                                Log.d("UI", "enableDayMonitoring() invoked")
                            } catch (t: Throwable) {
                                Log.e("UI", "enableDayMonitoring() FAILED", t)
                            }
                        }
                    },
                    onDisableMdMonitoring = {
                        Log.d("UI", "DISABLE MD MONITORING BUTTON CLICKED")
                        scope.launch {
                            try {
                                serviceStarter.disableDayMonitoring()

                                Log.d("UI", "disableDayMonitoring() invoked")
                            } catch (t: Throwable) {
                                Log.e("UI", "disableDayMonitoring() FAILED", t)
                            }
                        }
                    },
                    onStopTrip = {
                        Log.d("UI", "STOP BUTTON CLICKED")
                        scope.launch {
                            try {
                                graph.facade.stopTrip()
                                Log.d("UI", "stopTrip() invoked")
                            } catch (t: Throwable) {
                                Log.e("UI", "stopTrip() FAILED", t)
                            }
                        }
                    },
                    onFlushNow = {
                        Log.d("UI", "FLUSH BUTTON CLICKED")
                        scope.launch {
                            try {
                                graph.facade.flushNow()
                                Log.d("UI", "flushNow() invoked")
                            } catch (t: Throwable) {
                                Log.e("UI", "flushNow() FAILED", t)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun requestActivityRecognitionPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val permission = Manifest.permission.ACTIVITY_RECOGNITION

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            activityRecognitionPermissionLauncher.launch(permission)
        }
    }


}

@Composable

private fun DebugTelemetryScreen(
    state: TripRuntimeState,
    driverIdInput: String,
    passwordInput: String,
    prepareStatus: String,
    registerStatus: String,
    loginStatus: String,
    deleteStatus: String,
    onDriverIdChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPrepareDriver: () -> Unit,
    onRegisterDriver: () -> Unit,
    onLoginDriver: () -> Unit,
    onDeleteAccount: () -> Unit,
    onStartSingleTrip: () -> Unit,
    onEnableMdMonitoring: () -> Unit,
    onDisableMdMonitoring: () -> Unit,
    onStopTrip: () -> Unit,
    onFlushNow: () -> Unit,
){
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Telemetry debug")

        HorizontalDivider()

        OutlinedTextField(
            value = driverIdInput,
            onValueChange = onDriverIdChanged,
            label = { Text("driver_id") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )

        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChanged,
            label = { Text("password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Button(onClick = onPrepareDriver) {
            Text("Prepare driver")
        }

        Text(text = "prepareStatus: $prepareStatus")

        Button(onClick = onRegisterDriver) {
            Text("Register driver")
        }

        Text(text = "registerStatus: $registerStatus")

        Button(onClick = onLoginDriver) {
            Text("Login driver")
        }

        Text(text = "loginStatus: $loginStatus")

        Button(onClick = onDeleteAccount) {
            Text("Delete account")
        }

        Text(text = "deleteStatus: $deleteStatus")

        HorizontalDivider()

        Text(text = "sessionId: ${state.sessionId ?: "-"}")
        Text(text = "trackingMode: ${state.trackingMode ?: "-"}")
        Text(text = "telemetryMode: ${state.telemetryMode}")
        Text(text = "pendingFinish: ${state.pendingFinish}")
        Text(text = "finishUiState: ${state.finishUiState}")
        Text(text = "lastFinishError: ${state.lastFinishError ?: "-"}")
        Text(text = "lastTripReport.sessionId: ${state.lastTripReport?.sessionId ?: "-"}")
        Text(text = "dayMonitoringEnabled: ${state.dayMonitoringEnabled}")
        Text(text = "dayMonitoringAutoTripActive: ${state.dayMonitoringAutoTripActive}")
        Text(text = "dayMonitoringAutoStartedSessionId: ${state.dayMonitoringAutoStartedSessionId ?: "-"}")
        Text(text = "startedAt: ${state.startedAt ?: "-"}")
        Text(text = "lastSampleAt: ${state.lastSampleAt ?: "-"}")
        Text(text = "lastLocationAt: ${state.lastLocationAt ?: "-"}")
        Text(text = "lastEventAt: ${state.lastEventAt ?: "-"}")
        Text(text = "distanceM: ${"%.2f".format(state.distanceM)}")
        Text(text = "isForegroundCollection: ${state.isForegroundCollection}")

        HorizontalDivider()

        Button(onClick = onStartSingleTrip) {
            Text("Single trip")
        }

        Button(onClick = onEnableMdMonitoring) {
            Text("MD monitoring")
        }

        Button(onClick = onDisableMdMonitoring) {
            Text("Disable MD monitoring")
        }

        Button(onClick = onStopTrip) {
            Text("Stop trip")
        }

        Button(onClick = onFlushNow) {
            Text("Flush now")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugTelemetryScreenPreview() {
    Android_TelemetryTheme {
        DebugTelemetryScreen(
            state = TripRuntimeState(),
            driverIdInput = "analitik7",

            passwordInput = "secret123",
            prepareStatus = "-",
            registerStatus = "-",
            loginStatus = "-",
            deleteStatus = "-",
            onDriverIdChanged = {},
            onPasswordChanged = {},
            onPrepareDriver = {},
            onRegisterDriver = {},
            onLoginDriver = {},
            onDeleteAccount = {},
            onStartSingleTrip = {},
            onEnableMdMonitoring = {},
            onDisableMdMonitoring = {},
            onStopTrip = {},
            onFlushNow = {},
        )
    }
}