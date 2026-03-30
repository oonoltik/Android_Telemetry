package com.alex.android_telemetry.telemetry.delivery

import android.util.Log
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import com.alex.android_telemetry.telemetry.delivery.api.DeliveryRoute
import com.alex.android_telemetry.telemetry.delivery.api.TelemetryApiResult
import com.alex.android_telemetry.telemetry.delivery.api.TelemetryDeliveryApi
import com.alex.android_telemetry.telemetry.ingest.repository.TelemetryOutboxRepository
import kotlinx.datetime.Clock

class TelemetryDeliveryProcessor(
    private val repository: TelemetryOutboxRepository,
    private val api: TelemetryDeliveryApi,
    private val retryDecider: TelemetryRetryDecider,
    private val backoffCalculator: TelemetryBackoffCalculator,
    private val policy: TelemetryDeliveryPolicy,
    private val authManager: TelemetryAuthManager,
    private val onBatchDelivered: suspend (sessionId: String, route: DeliveryRoute) -> Unit,
    private val clock: Clock = Clock.System,
) {
    suspend fun runOnce(): DeliveryRunResult {
        val now = clock.now().toEpochMilliseconds()

        Log.d("TelemetryDelivery", "runOnce(): start now=$now")

        repository.reclaimStaleInFlight(now - policy.inflightTimeoutMs)

        val pendingCount = repository.countReadyForDelivery(now)
        Log.d("TelemetryDelivery", "runOnce(): readyForDelivery=$pendingCount")

        val items = repository.claimNextForDelivery(policy.maxBatchCountPerRun)
        Log.d("TelemetryDelivery", "runOnce(): claimed=${items.size}")

        if (items.isEmpty()) {
            Log.d("TelemetryDelivery", "runOnce(): idle, nothing to deliver")
            return DeliveryRunResult.Idle
        }

        var hasRetryableLeft = false
        var deliveredCount = 0

        for (item in items) {
            Log.d(
                "TelemetryDelivery",
                "runOnce(): sending id=${item.id} batchId=${item.batchId} attempt=${item.attemptCount}"
            )

            when (val result = api.sendBatch(item.payloadJson)) {
                is TelemetryApiResult.Success -> {
                    repository.markDelivered(
                        id = item.id,
                        serverStatus = result.status,
                        duplicate = result.duplicate,
                    )

                    onBatchDelivered(item.sessionId, result.route)
                    deliveredCount++

                    Log.d(
                        "TelemetryDelivery",
                        "runOnce(): delivered id=${item.id} batchId=${item.batchId} duplicate=${result.duplicate} route=${result.route}"
                    )
                }

                is TelemetryApiResult.NetworkError -> {
                    val attempt = item.attemptCount + 1

                    Log.d(
                        "TelemetryDelivery",
                        "runOnce(): network error id=${item.id} batchId=${item.batchId} message=${result.message}"
                    )

                    when (
                        val decision = retryDecider.decide(
                            DeliveryFailure.Network(result.message),
                            attempt,
                        )
                    ) {
                        is RetryDecision.Retry -> {
                            val nextRetryAt = backoffCalculator.nextRetryAtEpochMs(now, attempt)

                            repository.markRetryWait(
                                id = item.id,
                                attemptCount = attempt,
                                httpCode = null,
                                error = decision.reason,
                                nextRetryAtEpochMs = nextRetryAt,
                            )

                            hasRetryableLeft = true

                            Log.d(
                                "TelemetryDelivery",
                                "runOnce(): retry scheduled id=${item.id} nextRetryAt=$nextRetryAt"
                            )
                        }

                        is RetryDecision.FailTerminal -> {
                            repository.markTerminalFailed(item.id, null, decision.reason)
                            Log.d(
                                "TelemetryDelivery",
                                "runOnce(): terminal fail id=${item.id} reason=${decision.reason}"
                            )
                        }

                        is RetryDecision.FailAuth -> {
                            authManager.invalidateToken()
                            repository.markRetryWait(
                                id = item.id,
                                attemptCount = attempt,
                                httpCode = null,
                                error = decision.reason,
                                nextRetryAtEpochMs = now + 1_000L,
                            )
                            hasRetryableLeft = true

                            Log.d(
                                "TelemetryDelivery",
                                "runOnce(): auth retry scheduled id=${item.id} reason=${decision.reason}"
                            )
                        }
                    }
                }

                is TelemetryApiResult.HttpError -> {
                    val attempt = item.attemptCount + 1

                    Log.d(
                        "TelemetryDelivery",
                        "runOnce(): http error id=${item.id} code=${result.code}"
                    )

                    when (
                        val decision = retryDecider.decide(
                            DeliveryFailure.Http(result.code, result.body),
                            attempt,
                        )
                    ) {
                        is RetryDecision.Retry -> {
                            val nextRetryAt = backoffCalculator.nextRetryAtEpochMs(now, attempt)

                            repository.markRetryWait(
                                id = item.id,
                                attemptCount = attempt,
                                httpCode = result.code,
                                error = decision.reason,
                                nextRetryAtEpochMs = nextRetryAt,
                            )

                            hasRetryableLeft = true

                            Log.d(
                                "TelemetryDelivery",
                                "runOnce(): retry scheduled id=${item.id} code=${result.code} nextRetryAt=$nextRetryAt"
                            )
                        }

                        is RetryDecision.FailTerminal -> {
                            repository.markTerminalFailed(item.id, result.code, decision.reason)
                            Log.d(
                                "TelemetryDelivery",
                                "runOnce(): terminal fail id=${item.id} code=${result.code}"
                            )
                        }

                        is RetryDecision.FailAuth -> {
                            authManager.invalidateToken()
                            repository.markRetryWait(
                                id = item.id,
                                attemptCount = attempt,
                                httpCode = result.code,
                                error = decision.reason,
                                nextRetryAtEpochMs = now + 1_000L,
                            )
                            hasRetryableLeft = true

                            Log.d(
                                "TelemetryDelivery",
                                "runOnce(): auth retry scheduled id=${item.id} code=${result.code}"
                            )
                        }
                    }
                }
            }
        }

        val finalResult = if (hasRetryableLeft || deliveredCount > 0) {
            DeliveryRunResult.Progress(deliveredCount = deliveredCount)
        } else {
            DeliveryRunResult.Idle
        }

        Log.d("TelemetryDelivery", "runOnce(): completed result=$finalResult")
        return finalResult
    }
}

sealed interface DeliveryRunResult {
    data object Idle : DeliveryRunResult
    data class Progress(val deliveredCount: Int) : DeliveryRunResult
}