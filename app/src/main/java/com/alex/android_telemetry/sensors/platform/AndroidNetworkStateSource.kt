package com.alex.android_telemetry.sensors.platform

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.alex.android_telemetry.sensors.api.NetworkStateSource
import com.alex.android_telemetry.telemetry.domain.model.NetworkStateSnapshot
import kotlinx.datetime.Clock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


class AndroidNetworkStateSource(
    private val connectivityManager: ConnectivityManager,
) : NetworkStateSource {

    override val snapshots: Flow<NetworkStateSnapshot> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitSnapshot(network)
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = emitSnapshot(network)
            override fun onLost(network: Network) {
                trySend(NetworkStateSnapshot(timestamp = Clock.System.now(), status = "lost"))
            }

            private fun emitSnapshot(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                trySend(
                    NetworkStateSnapshot(
                        timestamp = Clock.System.now(),
                        status = when {
                            capabilities == null -> "unavailable"
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "validated"
                            else -> "available"
                        },
                        interfaceType = capabilities?.toInterfaceType(),
                        isExpensive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?.not(),
                        isConstrained = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)?.not(),
                    ),
                )
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
}

private fun NetworkCapabilities.toInterfaceType(): String = when {
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
    hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
    else -> "unknown"
}
