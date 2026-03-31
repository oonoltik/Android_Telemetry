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
                baseUrl = TelemetryBackendConfig.EU_BASE_URL,
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
                    pendingTripFinishStore.getAll().map { it.sessionId }.toSet()
                },
                onBatchDelivered = { sessionId, route ->
                    val before = tripDeliveryStatsStore.get(sessionId)
                    tripDeliveryStatsStore.recordBatchDelivery(sessionId, route)
                    val after = tripDeliveryStatsStore.get(sessionId)

                    if (pendingTripFinishStore.exists(sessionId)) {
                        if (before.deliveredBatches == 0 && after.deliveredBatches > 0) {
                            Log.d(
                                "TelemetryTrip",
                                "first delivered batch for pending finish sessionId=$sessionId -> scheduleFinishRetryImmediate()"
                            )
                        } else {
                            Log.d(
                                "TelemetryTrip",
                                "delivered batch for pending finish sessionId=$sessionId -> scheduleFinishRetryImmediate()"
                            )
                        }

                        finishRetryScheduler.scheduleImmediate()
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