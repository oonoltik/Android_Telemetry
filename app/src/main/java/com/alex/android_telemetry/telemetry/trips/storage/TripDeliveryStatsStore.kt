package com.alex.android_telemetry.telemetry.trips.storage

import android.content.Context
import com.alex.android_telemetry.telemetry.delivery.api.DeliveryRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TripDeliveryStatsStore(
    context: Context,
    private val json: Json,
) {
    @Serializable
    data class DeliveryStats(
        val euBatches: Int = 0,
        val ruBatches: Int = 0,
    ) {
        val deliveredBatches: Int
            get() = euBatches + ruBatches
    }

    private val prefs = context.getSharedPreferences("telemetry_delivery_stats", Context.MODE_PRIVATE)
    private val statsKeyPrefix = "delivery_stats_v1_"

    fun get(sessionId: String): DeliveryStats {
        val raw = prefs.getString(key(sessionId), null) ?: return DeliveryStats()
        return runCatching {
            json.decodeFromString(DeliveryStats.serializer(), raw)
        }.getOrDefault(DeliveryStats())
    }

    fun recordBatchDelivery(sessionId: String, route: DeliveryRoute) {
        val current = get(sessionId)
        val updated = when (route) {
            DeliveryRoute.EU -> current.copy(euBatches = current.euBatches + 1)
            DeliveryRoute.RU -> current.copy(ruBatches = current.ruBatches + 1)
        }

        prefs.edit()
            .putString(key(sessionId), json.encodeToString(updated))
            .apply()
    }

    fun clear(sessionId: String) {
        prefs.edit().remove(key(sessionId)).apply()
    }

    private fun key(sessionId: String): String = statsKeyPrefix + sessionId
}