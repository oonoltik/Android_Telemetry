package com.alex.android_telemetry.telemetry.delivery

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
    private val clock: Clock = Clock.System,
) {
    suspend fun runOnce(): DeliveryRunResult {
        val now = clock.now().toEpochMilliseconds()

        repository.reclaimStaleInFlight(now - policy.inflightTimeoutMs)

        val items = repository.claimNextForDelivery(policy.maxBatchCountPerRun)
        if (items.isEmpty()) return DeliveryRunResult.Idle

        var hasRetryableLeft = false
        var deliveredCount = 0

        for (item in items) {
            when (val result = api.sendBatch(item.payloadJson)) {
                is TelemetryApiResult.Success -> {
                    repository.markDelivered(
                        id = item.id,
                        serverStatus = result.status,
                        duplicate = result.duplicate,
                    )
                    deliveredCount++
                }

                is TelemetryApiResult.NetworkError -> {
                    val attempt = item.attemptCount + 1
                    when (val decision = retryDecider.decide(
                        DeliveryFailure.Network(result.message),
                        attempt,
                    )) {
                        is RetryDecision.Retry -> {
                            repository.markRetryWait(
                                id = item.id,
                                attemptCount = attempt,
                                httpCode = null,
                                error = decision.reason,
                                nextRetryAtEpochMs = backoffCalculator.nextRetryAtEpochMs(now, item.attemptCount),
                            )
                            hasRetryableLeft = true
                        }
                        is RetryDecision.FailTerminal -> {
                            repository.markTerminalFailed(item.id, null, decision.reason)
                        }
                        is RetryDecision.FailAuth -> {
                            repository.markAuthFailed(item.id, null, decision.reason)
                        }
                    }
                }

                is TelemetryApiResult.HttpError -> {
                    val attempt = item.attemptCount + 1
                    when (val decision = retryDecider.decide(
                        DeliveryFailure.Http(result.code, result.body),
                        attempt,
                    )) {
                        is RetryDecision.Retry -> {
                            repository.markRetryWait(
                                id = item.id,
                                attemptCount = attempt,
                                httpCode = result.code,
                                error = decision.reason,
                                nextRetryAtEpochMs = backoffCalculator.nextRetryAtEpochMs(now, item.attemptCount),
                            )
                            hasRetryableLeft = true
                        }
                        is RetryDecision.FailTerminal -> {
                            repository.markTerminalFailed(item.id, result.code, decision.reason)
                        }
                        is RetryDecision.FailAuth -> {
                            repository.markAuthFailed(item.id, result.code, decision.reason)
                        }
                    }
                }
            }
        }

        return if (hasRetryableLeft || deliveredCount == items.size) {
            DeliveryRunResult.Progress(deliveredCount = deliveredCount)
        } else {
            DeliveryRunResult.Idle
        }
    }
}

sealed interface DeliveryRunResult {
    data object Idle : DeliveryRunResult
    data class Progress(val deliveredCount: Int) : DeliveryRunResult
}