package com.alex.android_telemetry.telemetry.delivery

import android.content.Context
import android.util.Log
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthApi
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import com.alex.android_telemetry.telemetry.auth.TelemetryDeviceIdProvider
import com.alex.android_telemetry.telemetry.auth.TelemetryKeyIdStore
import com.alex.android_telemetry.telemetry.auth.TelemetryTokenStore
import com.alex.android_telemetry.telemetry.delivery.api.DeliveryRoute
import com.alex.android_telemetry.telemetry.delivery.api.OkHttpTelemetryDeliveryApi
import com.alex.android_telemetry.telemetry.delivery.storage.TelemetryDatabase
import com.alex.android_telemetry.telemetry.domain.TripFinishManager
import com.alex.android_telemetry.telemetry.domain.TripRepository
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import com.alex.android_telemetry.telemetry.trips.api.OkHttpTripApi
import com.alex.android_telemetry.telemetry.trips.finish.FinishRetryScheduler
import com.alex.android_telemetry.telemetry.trips.storage.PendingTripFinishStore
import com.alex.android_telemetry.telemetry.trips.storage.TripDeliveryStatsStore
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import com.alex.android_telemetry.telemetry.trips.api.FallbackTripApi
import com.alex.android_telemetry.telemetry.trips.api.TripApi

class TelemetryDeliveryGraph(
    val processor: TelemetryDeliveryProcessor,
    val tripRepository: TripRepository,
) {
    companion object {
        fun from(context: Context): TelemetryDeliveryGraph {
            val db = TelemetryDatabase.get(context)
            val dao = db.telemetryOutboxDao()

            val repository = TelemetryOutboxRepository(dao)

            val policy = TelemetryDeliveryPolicy()
            val retryDecider = TelemetryRetryDecider(policy)
            val backoff = TelemetryBackoffCalculator(policy)

            val okHttpClient = OkHttpClient()

            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

            val deviceIdProvider = TelemetryDeviceIdProvider(context)
            val tokenStore = TelemetryTokenStore(context)
            val keyIdStore = TelemetryKeyIdStore(context)

            val authApi = TelemetryAuthApi(
                euBaseUrl = TelemetryBackendConfig.EU_BASE_URL,
                ruBaseUrl = TelemetryBackendConfig.RU_BASE_URL,
                androidRegisterKey = BuildConfig.ANDROID_REGISTER_KEY,
                client = okHttpClient,
                json = json,
            )

            val authManager = TelemetryAuthManager(
                authApi = authApi,
                tokenStore = tokenStore,
                keyIdStore = keyIdStore,
                deviceIdProvider = deviceIdProvider,
            )

            val authTokenProvider: suspend () -> String? = {
                authManager.getValidToken()
            }

            val onUnauthorized: suspend () -> Unit = {
                authManager.invalidateToken()
            }

            val pendingTripFinishStore = PendingTripFinishStore(context, json)
            val tripDeliveryStatsStore = TripDeliveryStatsStore(context, json)
            val runtimeStateStore =
                com.alex.android_telemetry.telemetry.runtime.PersistentTripRuntimeStateStore(context)
            val finishRetryScheduler = FinishRetryScheduler(context)

            val euTripApi = OkHttpTripApi(
                baseUrl = TelemetryBackendConfig.EU_BASE_URL,
                authTokenProvider = { deviceId -> authManager.getValidToken() },
                onUnauthorized = { authManager.invalidateToken() },
                client = okHttpClient,
                json = json,
            )

            val ruTripApi = OkHttpTripApi(
                baseUrl = TelemetryBackendConfig.RU_BASE_URL,
                authTokenProvider = { deviceId -> authManager.getValidToken() },
                onUnauthorized = { authManager.invalidateToken() },
                client = okHttpClient,
                json = json,
            )

            val tripApi: TripApi = FallbackTripApi(
                primary = euTripApi,
                fallback = ruTripApi,
            )

            val tripFinishManager = TripFinishManager(
                tripApi = tripApi,
                pendingStore = pendingTripFinishStore,
                deliveryStatsStore = tripDeliveryStatsStore,
                finishRetryScheduler = finishRetryScheduler,
            )

            val tripRepository = TripRepository(
                tripApi = tripApi,
                tripFinishManager = tripFinishManager,
            )

            val euApi = OkHttpTelemetryDeliveryApi(
                baseUrl = TelemetryBackendConfig.EU_BASE_URL,
                route = DeliveryRoute.EU,
                authTokenProvider = authTokenProvider,
                onUnauthorized = onUnauthorized,
                client = okHttpClient,
                json = json,
            )

            val ruApi = OkHttpTelemetryDeliveryApi(
                baseUrl = TelemetryBackendConfig.RU_BASE_URL,
                route = DeliveryRoute.RU,
                authTokenProvider = authTokenProvider,
                onUnauthorized = onUnauthorized,
                client = okHttpClient,
                json = json,
            )

            val deliveryApi = FallbackTelemetryDeliveryApi(
                primary = euApi,
                fallback = ruApi,
            )

            val processor = TelemetryDeliveryProcessor(
                repository = repository,
                api = deliveryApi,
                retryDecider = retryDecider,
                backoffCalculator = backoff,
                policy = policy,
                authManager = authManager,
                getPrioritySessionIds = {
                    val restored = runtimeStateStore.restore()

                    val activeSessionId =
                        if (restored?.sessionId != null &&
                            restored.telemetryMode == com.alex.android_telemetry.telemetry.domain.model.TelemetryMode.COLLECTING
                        ) {
                            restored.sessionId
                        } else {
                            null
                        }

                    val pendingSessions =
                        pendingTripFinishStore.getAll().map { it.sessionId }

                    val result = buildSet {
                        activeSessionId?.let { add(it) }
                        addAll(pendingSessions)
                    }

                    Log.d(
                        "TelemetryDelivery",
                        "prioritySessions resolved active=$activeSessionId pending=$pendingSessions result=$result"
                    )

                    result
                },
                onBatchDelivered = { sessionId, route ->
                    val hadPendingFinish = pendingTripFinishStore.exists(sessionId)
                    val before = tripDeliveryStatsStore.get(sessionId)

                    Log.d(
                        "TelemetryTrip",
                        "onBatchDelivered(): before sessionId=$sessionId route=$route deliveredBatches=${before.deliveredBatches} hadPendingFinish=$hadPendingFinish"
                    )

                    tripDeliveryStatsStore.recordBatchDelivery(sessionId, route)

                    val after = tripDeliveryStatsStore.get(sessionId)

                    Log.d(
                        "TelemetryTrip",
                        "onBatchDelivered(): after sessionId=$sessionId route=$route deliveredBatches=${after.deliveredBatches} hadPendingFinish=$hadPendingFinish"
                    )

                    if (hadPendingFinish) {
                        if (before.deliveredBatches == 0 && after.deliveredBatches > 0) {
                            Log.d(
                                "TelemetryTrip",
                                "onBatchDelivered(): first delivered batch for pending finish sessionId=$sessionId route=$route deliveredBatches=${after.deliveredBatches} -> scheduleFinishRetryImmediate()"
                            )
                            finishRetryScheduler.scheduleImmediate()
                        } else {
                            Log.d(
                                "TelemetryTrip",
                                "onBatchDelivered(): delivered with existing pending finish sessionId=$sessionId route=$route deliveredBatches=${after.deliveredBatches}"
                            )
                        }
                    }
                },
            )

            return TelemetryDeliveryGraph(
                processor = processor,
                tripRepository = tripRepository,
            )
        }
    }
}