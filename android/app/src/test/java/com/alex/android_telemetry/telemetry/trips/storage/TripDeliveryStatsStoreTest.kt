package com.alex.android_telemetry.telemetry.trips.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alex.android_telemetry.telemetry.delivery.api.DeliveryRoute
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDeliveryStatsStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun empty_store_returns_zero_counters() {
        val store = TripDeliveryStatsStore(FakePrefsContext(), json)

        val stats = store.get("session-1")

        assertEquals(0, stats.deliveredBatches)
        assertEquals(0, stats.euBatches)
        assertEquals(0, stats.ruBatches)
        assertFalse(stats.deliveredBatches > 0)
        assertEquals(0, store.getDeliveredBatches("session-1"))
    }

    @Test
    fun recordDeliveredBatch_increments_total_and_eu_route_counter() {
        val store = TripDeliveryStatsStore(FakePrefsContext(), json)

        val stats = store.recordBatchDelivery(
            sessionId = "session-1",
            route = DeliveryRoute.EU,
        )

        assertEquals(1, stats.deliveredBatches)
        assertEquals(1, stats.euBatches)
        assertEquals(0, stats.ruBatches)
        assertTrue(stats.deliveredBatches > 0)
        assertEquals(1, store.getDeliveredBatches("session-1"))
    }

    @Test
    fun recordDeliveredBatch_increments_total_and_ru_route_counter() {
        val store = TripDeliveryStatsStore(FakePrefsContext(), json)

        val stats = store.recordBatchDelivery(
            sessionId = "session-1",
            route = DeliveryRoute.RU,
        )

        assertEquals(1, stats.deliveredBatches)
        assertEquals(0, stats.euBatches)
        assertEquals(1, stats.ruBatches)
        assertTrue(stats.deliveredBatches > 0)
        assertEquals(1, store.getDeliveredBatches("session-1"))
    }

    @Test
    fun recordDeliveredBatch_persists_mixed_route_counters() {
        val context = FakePrefsContext()
        val store1 = TripDeliveryStatsStore(context, json)

        store1.recordBatchDelivery("session-1", DeliveryRoute.EU)
        store1.recordBatchDelivery("session-1", DeliveryRoute.RU)
        store1.recordBatchDelivery("session-1", DeliveryRoute.EU)

        val store2 = TripDeliveryStatsStore(context, json)
        val loaded = store2.get("session-1")

        assertEquals(3, loaded.deliveredBatches)
        assertEquals(2, loaded.euBatches)
        assertEquals(1, loaded.ruBatches)
        assertTrue(loaded.deliveredBatches > 0)
        assertEquals(3, store2.getDeliveredBatches("session-1"))
    }

    @Test
    fun recordDeliveredBatch_keeps_sessions_independent() {
        val store = TripDeliveryStatsStore(FakePrefsContext(), json)

        store.recordBatchDelivery("session-1", DeliveryRoute.EU)
        store.recordBatchDelivery("session-1", DeliveryRoute.EU)
        store.recordBatchDelivery("session-2", DeliveryRoute.RU)

        val s1 = store.get("session-1")
        val s2 = store.get("session-2")

        assertEquals(2, s1.deliveredBatches)
        assertEquals(2, s1.euBatches)
        assertEquals(0, s1.ruBatches)

        assertEquals(1, s2.deliveredBatches)
        assertEquals(0, s2.euBatches)
        assertEquals(1, s2.ruBatches)
    }

    @Test
    fun clear_removes_only_matching_session() {
        val store = TripDeliveryStatsStore(FakePrefsContext(), json)

        store.recordBatchDelivery("session-1", DeliveryRoute.EU)
        store.recordBatchDelivery("session-2", DeliveryRoute.RU)

        store.clear("session-1")

        val s1 = store.get("session-1")
        val s2 = store.get("session-2")

        assertEquals(0, s1.deliveredBatches)
        assertEquals(0, s1.euBatches)
        assertEquals(0, s1.ruBatches)
        assertFalse(s1.deliveredBatches > 0)

        assertEquals(1, s2.deliveredBatches)
        assertEquals(0, s2.euBatches)
        assertEquals(1, s2.ruBatches)
        assertTrue(s2.deliveredBatches > 0)
    }

    @Test
    fun corrupted_json_returns_empty_stats_instead_of_crashing() {
        val context = FakePrefsContext()
        context.sharedPreferences.raw["items"] = "{broken-json"

        val store = TripDeliveryStatsStore(context, json)

        val stats = store.get("session-1")

        assertEquals(0, stats.deliveredBatches)
        assertEquals(0, stats.euBatches)
        assertEquals(0, stats.ruBatches)
        assertFalse(stats.deliveredBatches > 0)
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
        ): SharedPreferences.Editor = this

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = this

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = this

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = this

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = this
    }
}