package com.alex.android_telemetry.telemetry.trips.storage

import android.content.Context
import com.alex.android_telemetry.telemetry.trips.api.PendingTripFinishDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class PendingTripFinishStore(
    context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("telemetry_trip_finish", Context.MODE_PRIVATE)

    fun getAll(): List<PendingTripFinishDto> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PendingTripFinishDto.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun upsert(item: PendingTripFinishDto) {
        val current = getAll().toMutableList()
        val idx = current.indexOfFirst { it.sessionId == item.sessionId }
        if (idx >= 0) current[idx] = item else current.add(item)
        saveAll(current)
    }

    fun remove(sessionId: String) {
        saveAll(getAll().filterNot { it.sessionId == sessionId })
    }

    fun exists(sessionId: String): Boolean {
        return getAll().any { it.sessionId == sessionId }
    }

    private fun saveAll(items: List<PendingTripFinishDto>) {
        prefs.edit()
            .putString(KEY_ITEMS, json.encodeToString(ListSerializer(PendingTripFinishDto.serializer()), items))
            .apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}

