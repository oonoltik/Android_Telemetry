package com.alex.android_telemetry.telemetry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSecureStorageContractTest {

    @Test
    fun unauthorized_401_clears_token_notifies_auth_and_keeps_payload_pending() {
        val secureStore = FakeSecureAuthStore()
        val outbox = FakeAuthOutbox()
        val authEvents = FakeUnauthorizedEventSink()
        val api = FakeAuthTelemetryApi(response = AuthApiResponse.Unauthorized)

        secureStore.saveToken("token-old")
        outbox.enqueue(
            batchId = "batch-1",
            payload = """{"batch_seq":1}""",
        )

        val sender = FakeAuthenticatedTelemetrySender(
            secureStore = secureStore,
            outbox = outbox,
            authEvents = authEvents,
            api = api,
            attestation = FakeAttestationGate(valid = true),
            environment = RuntimeEnvironment.Production,
        )

        sender.flushPending()

        assertEquals(null, secureStore.token)
        assertEquals(listOf("unauthorized"), authEvents.events)
        assertEquals(listOf("batch-1"), outbox.pendingIds())
        assertEquals(1, api.calls.size)
        assertEquals("Bearer token-old", api.calls.single().authorizationHeader)
    }

    @Test
    fun stable_device_id_is_reused_across_secure_store_restart() {
        val disk = linkedMapOf<String, String>()

        val beforeRestart = FakeSecureIdentityStore(disk)
        val firstDeviceId = beforeRestart.getOrCreateDeviceId()

        val afterRestart = FakeSecureIdentityStore(disk)
        val secondDeviceId = afterRestart.getOrCreateDeviceId()

        assertEquals(firstDeviceId, secondDeviceId)
        assertEquals(1, disk.size)
    }

    @Test
    fun token_is_reused_across_restart_for_authenticated_replay() {
        val authDisk = linkedMapOf<String, String>()
        val secureStoreBeforeRestart = FakeSecureAuthStore(authDisk)

        secureStoreBeforeRestart.saveToken("token-stable")

        val secureStoreAfterRestart = FakeSecureAuthStore(authDisk)
        val outbox = FakeAuthOutbox()
        val api = FakeAuthTelemetryApi(response = AuthApiResponse.Success)

        outbox.enqueue(
            batchId = "batch-1",
            payload = """{"batch_seq":1}""",
        )

        val sender = FakeAuthenticatedTelemetrySender(
            secureStore = secureStoreAfterRestart,
            outbox = outbox,
            authEvents = FakeUnauthorizedEventSink(),
            api = api,
            attestation = FakeAttestationGate(valid = true),
            environment = RuntimeEnvironment.Production,
        )

        sender.flushPending()

        assertEquals("token-stable", secureStoreAfterRestart.token)
        assertEquals("Bearer token-stable", api.calls.single().authorizationHeader)
        assertTrue(outbox.pendingIds().isEmpty())
    }

    @Test
    fun production_blocks_telemetry_when_attestation_is_missing_or_invalid() {
        val secureStore = FakeSecureAuthStore()
        val outbox = FakeAuthOutbox()
        val api = FakeAuthTelemetryApi(response = AuthApiResponse.Success)

        secureStore.saveToken("token-prod")
        outbox.enqueue(
            batchId = "batch-1",
            payload = """{"batch_seq":1}""",
        )

        val sender = FakeAuthenticatedTelemetrySender(
            secureStore = secureStore,
            outbox = outbox,
            authEvents = FakeUnauthorizedEventSink(),
            api = api,
            attestation = FakeAttestationGate(valid = false),
            environment = RuntimeEnvironment.Production,
        )

        sender.flushPending()

        assertTrue(api.calls.isEmpty())
        assertEquals(listOf("batch-1"), outbox.pendingIds())
    }

    @Test
    fun debug_allows_telemetry_without_attestation_gate() {
        val secureStore = FakeSecureAuthStore()
        val outbox = FakeAuthOutbox()
        val api = FakeAuthTelemetryApi(response = AuthApiResponse.Success)

        secureStore.saveToken("token-debug")
        outbox.enqueue(
            batchId = "batch-1",
            payload = """{"batch_seq":1}""",
        )

        val sender = FakeAuthenticatedTelemetrySender(
            secureStore = secureStore,
            outbox = outbox,
            authEvents = FakeUnauthorizedEventSink(),
            api = api,
            attestation = FakeAttestationGate(valid = false),
            environment = RuntimeEnvironment.Debug,
        )

        sender.flushPending()

        assertEquals(1, api.calls.size)
        assertTrue(outbox.pendingIds().isEmpty())
    }

    @Test
    fun repeated_401_does_not_create_unauthorized_retry_storm() {
        val secureStore = FakeSecureAuthStore()
        val outbox = FakeAuthOutbox()
        val authEvents = FakeUnauthorizedEventSink()
        val api = FakeAuthTelemetryApi(response = AuthApiResponse.Unauthorized)

        secureStore.saveToken("token-old")
        outbox.enqueue(
            batchId = "batch-1",
            payload = """{"batch_seq":1}""",
        )

        val sender = FakeAuthenticatedTelemetrySender(
            secureStore = secureStore,
            outbox = outbox,
            authEvents = authEvents,
            api = api,
            attestation = FakeAttestationGate(valid = true),
            environment = RuntimeEnvironment.Production,
        )

        sender.flushPending()
        sender.flushPending()
        sender.flushPending()

        assertEquals(1, api.calls.size)
        assertEquals(listOf("unauthorized"), authEvents.events)
        assertEquals(listOf("batch-1"), outbox.pendingIds())
        assertEquals(null, secureStore.token)
    }
}

private enum class AuthApiResponse {
    Success,
    Unauthorized,
}

private enum class RuntimeEnvironment {
    Debug,
    Production,
}

private data class AuthApiCall(
    val authorizationHeader: String,
    val payload: String,
)

private class FakeSecureAuthStore(
    private val disk: LinkedHashMap<String, String> = linkedMapOf(),
) {
    var token: String?
        get() = disk["access_token"]
        private set(value) {
            if (value == null) {
                disk.remove("access_token")
            } else {
                disk["access_token"] = value
            }
        }

    fun saveToken(token: String) {
        this.token = token
    }

    fun clearToken() {
        token = null
    }
}

private class FakeSecureIdentityStore(
    private val disk: LinkedHashMap<String, String>,
) {
    fun getOrCreateDeviceId(): String {
        val existing = disk["device_id"]

        if (existing != null) {
            return existing
        }

        val created = "device-id-stable-1"
        disk["device_id"] = created

        return created
    }
}

private class FakeAuthOutbox {
    private val batches = linkedMapOf<String, String>()

    fun enqueue(
        batchId: String,
        payload: String,
    ) {
        batches[batchId] = payload
    }

    fun pending(): List<Pair<String, String>> {
        return batches.entries.map { it.key to it.value }
    }

    fun pendingIds(): List<String> {
        return batches.keys.toList()
    }

    fun remove(batchId: String) {
        batches.remove(batchId)
    }
}

private class FakeUnauthorizedEventSink {
    val events = mutableListOf<String>()

    private var unauthorizedEmitted = false

    fun emitUnauthorizedOnce() {
        if (unauthorizedEmitted) return

        unauthorizedEmitted = true
        events += "unauthorized"
    }
}

private class FakeAttestationGate(
    private val valid: Boolean,
) {
    fun isAllowed(environment: RuntimeEnvironment): Boolean {
        return environment == RuntimeEnvironment.Debug || valid
    }
}

private class FakeAuthTelemetryApi(
    private val response: AuthApiResponse,
) {
    val calls = mutableListOf<AuthApiCall>()

    fun postTelemetry(
        authorizationHeader: String,
        payload: String,
    ): AuthApiResponse {
        calls += AuthApiCall(
            authorizationHeader = authorizationHeader,
            payload = payload,
        )

        return response
    }
}

private class FakeAuthenticatedTelemetrySender(
    private val secureStore: FakeSecureAuthStore,
    private val outbox: FakeAuthOutbox,
    private val authEvents: FakeUnauthorizedEventSink,
    private val api: FakeAuthTelemetryApi,
    private val attestation: FakeAttestationGate,
    private val environment: RuntimeEnvironment,
) {
    fun flushPending() {
        val token = secureStore.token ?: return

        if (!attestation.isAllowed(environment)) {
            return
        }

        for ((batchId, payload) in outbox.pending()) {
            val response = api.postTelemetry(
                authorizationHeader = "Bearer $token",
                payload = payload,
            )

            when (response) {
                AuthApiResponse.Success -> outbox.remove(batchId)
                AuthApiResponse.Unauthorized -> {
                    secureStore.clearToken()
                    authEvents.emitUnauthorizedOnce()
                    return
                }
            }
        }
    }
}