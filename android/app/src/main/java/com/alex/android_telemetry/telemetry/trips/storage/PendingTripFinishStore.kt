package com.alex.android_telemetry.telemetry.trips.storage

import android.content.Context
import android.util.Log
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
        }.getOrElse {
            Log.e("TelemetryTrip", "PendingTripFinishStore.getAll(): decode failed ${it.message}", it)
            emptyList()
        }
    }

    fun getBySessionId(sessionId: String): PendingTripFinishDto? {
        val item = getAll().firstOrNull { it.sessionId == sessionId }
        Log.d(
            "TelemetryTrip",
            "PendingTripFinishStore.getBySessionId(): sessionId=$sessionId found=${item != null}"
        )
        return item
    }

    fun upsert(item: PendingTripFinishDto) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.sessionId == item.sessionId }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(item)
        }
        saveAll(current)

        Log.d(
            "TelemetryTrip",
            "PendingTripFinishStore.upsert(): sessionId=${item.sessionId} retryCount=${item.retryCount} queuedBecauseNoDeliveredBatches=${item.queuedBecauseNoDeliveredBatches}"
        )
    }

    fun markAttempt(
        sessionId: String,
        attemptedAt: String,
        errorMessage: String?,
    ) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.sessionId == sessionId }
        if (index < 0) {
            Log.d(
                "TelemetryTrip",
                "PendingTripFinishStore.markAttempt(): sessionId=$sessionId not found"
            )
            return
        }

        val item = current[index]
        current[index] = item.copy(
            retryCount = item.retryCount + 1,
            lastAttemptAt = attemptedAt,
            lastError = errorMessage,
        )
        saveAll(current)

        Log.d(
            "TelemetryTrip",
            "PendingTripFinishStore.markAttempt(): sessionId=$sessionId retryCount=${item.retryCount + 1} error=$errorMessage"
        )
    }

    fun remove(sessionId: String) {
        saveAll(getAll().filterNot { it.sessionId == sessionId })
        Log.d(
            "TelemetryTrip",
            "PendingTripFinishStore.remove(): sessionId=$sessionId removed=true"
        )
    }

    fun exists(sessionId: String): Boolean {
        val exists = getBySessionId(sessionId) != null
        Log.d(
            "TelemetryTrip",
            "PendingTripFinishStore.exists(): sessionId=$sessionId exists=$exists"
        )
        return exists
    }

    private fun saveAll(items: List<PendingTripFinishDto>) {
        val raw = json.encodeToString(ListSerializer(PendingTripFinishDto.serializer()), items)
        prefs.edit().putString(KEY_ITEMS, raw).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}