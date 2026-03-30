package com.alex.android_telemetry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alex.android_telemetry.telemetry.delivery.TelemetryDeliveryGraph
import com.alex.android_telemetry.ui.theme.Android_TelemetryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var graph: TelemetryAppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("TelemetryTrip", "🔥 APP START NEW BUILD 🔥")

        graph = TelemetryAppGraph.get(applicationContext)

        lifecycleScope.launch {
            graph.facade.restore()
            graph.scheduler.schedulePeriodic()

            runCatching {
                TelemetryDeliveryGraph.from(applicationContext)
                    .tripRepository
                    .retryPendingFinishes()
            }.onFailure {
                Log.e("TelemetryTrip", "retryPendingFinishes on app start failed", it)
            }

            if (hasLocationPermission()) {
                Log.d("TelemetryDelivery", "Location permission granted, starting trip")
                graph.facade.startTrip()
            } else {
                Log.d("TelemetryDelivery", "Location permission missing, trip start skipped")
            }
        }

        setContent {
            Android_TelemetryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        lifecycleScope.launch {
            runCatching {
                Log.d("TelemetryTrip", "MainActivity onStop -> stopTrip")
                graph.facade.stopTrip()
            }.onFailure {
                Log.e("TelemetryTrip", "stopTrip from onStop failed", it)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Привет, это моё приложение Telemetry!",
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Android_TelemetryTheme {
        Greeting("Android")
    }
}