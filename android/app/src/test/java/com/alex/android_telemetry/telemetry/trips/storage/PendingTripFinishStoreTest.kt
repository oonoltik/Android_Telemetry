package com.alex.android_telemetry.telemetry.trips.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alex.android_telemetry.telemetry.trips.api.ClientAggDto
import com.alex.android_telemetry.telemetry.trips.api.ClientTripMetricsDto
import com.alex.android_telemetry.telemetry.trips.api.DeviceMetaDto
import com.alex.android_telemetry.telemetry.trips.api.PendingTripFinishDto
import com.alex.android_telemetry.telemetry.trips.api.TripCoreDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTripFinishStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun upsert_then_getBySessionId_persists_finish_payload_and_retry_metadata() {
        val context = FakePrefsContext()
        val store = PendingTripFinishStore(context, json)

        val item = pendingFinish(
            sessionId = "session-1",
            retryCount = 2,
            lastAttemptAt = "2026-04-30T12:11:00Z",
            lastError = "HTTP 500",
            queuedBecauseNoDeliveredBatches = true,
        )

        store.upsert(item)

        val loaded = store.getBySessionId("session-1")

        assertNotNull(loaded)
        loaded ?: return

        assertEquals("session-1", loaded.sessionId)
        assertEquals("driver-1", loaded.driverId)
        assertEquals("device-1", loaded.deviceId)
        assertEquals("2026-04-30T12:10:00Z", loaded.clientEndedAt)

        assertEquals(2, loaded.retryCount)
        assertEquals("2026-04-30T12:11:00Z", loaded.lastAttemptAt)
        assertEquals("HTTP 500", loaded.lastError)
        assertTrue(loaded.queuedBecauseNoDeliveredBatches)

        assertEquals("session-1", loaded.tripCore.sessionId)
        assertEquals("android", loaded.deviceMeta.platform)
        assertEquals("foreground", loaded.deviceContext?.get("app_state")?.toString()?.trim('"'))
        assertEquals("automotive", loaded.tailActivityContext?.get("dominant")?.toString()?.trim('"'))
    }

    @Test
    fun upsert_replaces_existing_session_instead_of_duplicating() {
        val context = FakePrefsContext()
        val store = PendingTripFinishStore(context, json)

        store.upsert(
            pendingFinish(
                sessionId = "session-1",
                retryCount = 0,
                lastError = null,
                queuedBecauseNoDeliveredBatches = true,
            )
        )

        store.upsert(
            pendingFinish(
                sessionId = "session-1",
                retryCount = 4,
                lastError = "HTTP 503",
                queuedBecauseNoDeliveredBatches = false,
            )
        )

        val all = store.getAll()

        assertEquals(1, all.size)
        assertEquals("session-1", all.single().sessionId)
        assertEquals(4, all.single().retryCount)
        assertEquals("HTTP 503", all.single().lastError)
        assertFalse(all.single().queuedBecauseNoDeliveredBatches)
    }

    @Test
    fun markAttempt_increments_retryCount_and_updates_attempt_metadata() {
        val context = FakePrefsContext()
        val store = PendingTripFinishStore(context, json)

        store.upsert(
            pendingFinish(
                sessionId = "session-1",
                retryCount = 2,
                lastAttemptAt = "2026-04-30T12:11:00Z",
                lastError = "HTTP 500",
                queuedBecauseNoDeliveredBatches = false,
            )
        )

        store.markAttempt(
            sessionId = "session-1",
            attemptedAt = "2026-04-30T12:12:00Z",
            errorMessage = "timeout",
        )

        val loaded = store.getBySessionId("session-1")

        assertNotNull(loaded)
        loaded ?: return

        assertEquals(3, loaded.retryCount)
        assertEquals("2026-04-30T12:12:00Z", loaded.lastAttemptAt)
        assertEquals("timeout", loaded.lastError)
    }

    @Test
    fun remove_deletes_only_matching_session() {
        val context = FakePrefsContext()
        val store = PendingTripFinishStore(context, json)

        store.upsert(pendingFinish(sessionId = "session-1"))
        store.upsert(pendingFinish(sessionId = "session-2"))

        assertTrue(store.exists("session-1"))
        assertTrue(store.exists("session-2"))

        store.remove("session-1")

        assertFalse(store.exists("session-1"))
        assertTrue(store.exists("session-2"))

        assertNull(store.getBySessionId("session-1"))
        assertNotNull(store.getBySessionId("session-2"))
        assertEquals(1, store.getAll().size)
    }

    @Test
    fun corrupted_json_returns_empty_list_instead_of_crashing() {
        val context = FakePrefsContext()
        context.sharedPreferences.raw["items"] = "{broken-json"

        val store = PendingTripFinishStore(context, json)

        assertEquals(emptyList<PendingTripFinishDto>(), store.getAll())
        assertFalse(store.exists("session-1"))
    }

    private fun pendingFinish(
        sessionId: String = "session-1",
        retryCount: Int = 0,
        lastAttemptAt: String? = null,
        lastError: String? = null,
        queuedBecauseNoDeliveredBatches: Boolean = false,
    ): PendingTripFinishDto {
        val empty = ClientAggDto(
            count = 0,
            sumIntensity = 0.0,
            maxIntensity = 0.0,
            countPerKm = 0.0,
            sumPerKm = 0.0,
        )

        return PendingTripFinishDto(
            sessionId = sessionId,
            driverId = "driver-1",
            deviceId = "device-1",
            clientEndedAt = "2026-04-30T12:10:00Z",
            createdAt = "2026-04-30T12:10:01Z",

            tripCore = TripCoreDto(
                tripId = sessionId,
                sessionId = sessionId,
                clientEndedAt = "2026-04-30T12:10:00Z",
            ),

            deviceMeta = DeviceMetaDto(
                platform = "android",
                appVersion = "1.0",
                appBuild = "1",
                iosVersion = "Android 15",
                deviceModel = "Pixel 8",
                locale = "ru-RU",
                timezone = "Europe/Moscow",
            ),

            trackingMode = "single_trip",
            transportMode = "car",
            tripDurationSec = 600.0,
            finishReason = "manual_stop",

            appVersion = "1.0",
            appBuild = "1",
            iosVersion = "Android 15",
            deviceModel = "Pixel 8",
            locale = "ru-RU",
            timezone = "Europe/Moscow",

            clientMetrics = ClientTripMetricsDto(
                tripDistanceM = 1000.0,
                tripDistanceKmFromGps = 1.0,
                brake = empty,
                accel = empty,
                road = empty,
                turn = empty,
            ),

            tripSummary = null,
            tripMetricsRaw = null,

            deviceContext = buildJsonObject {
                put("app_state", "foreground")
                put("battery_level", 0.8)
            },

            tailActivityContext = buildJsonObject {
                put("dominant", "automotive")
                put("automotive_share", 0.95)
            },

            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
            lastError = lastError,
            queuedBecauseNoDeliveredBatches = queuedBecauseNoDeliveredBatches,
        )
    }

    private class FakePrefsContext : ContextWrapper(null) {
        val sharedPreferences = FakeSharedPreferences()

        override fun getSharedPreferences(
            name: String?,
            mode: Int,
        ): SharedPreferences {
            return sharedPreferences
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val raw = linkedMapOf<String, String?>()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? {
            return raw[key] ?: defValue
        }

        override fun edit(): SharedPreferences.Editor {
            return FakeEditor(raw)
        }

        override fun contains(key: String?): Boolean {
            return raw.containsKey(key)
        }

        override fun getAll(): MutableMap<String, *> {
            return raw.toMutableMap()
        }

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? {
            return defValues
        }

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int {
            return defValue
        }

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long {
            return defValue
        }

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float {
            return defValue
        }

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean {
            return defValue
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }

    private class FakeEditor(
        private val raw: MutableMap<String, String?>,
    ) : SharedPreferences.Editor {

        private val pending = linkedMapOf<String, String?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
                removals.remove(key)
            }
            return this
        }

        override fun remove(
            key: String?,
        ): SharedPreferences.Editor {
            if (key != null) {
                removals.add(key)
                pending.remove(key)
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            pending.clear()
            removals.clear()
            return this
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            if (clearRequested) {
                raw.clear()
            }

            removals.forEach {
                raw.remove(it)
            }

            pending.forEach { (key, value) ->
                raw[key] = value
            }

            return true
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            return this
        }

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor {
            return this
        }

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor {
            return this
        }

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor {
            return this
        }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor {
            return this
        }
    }
}