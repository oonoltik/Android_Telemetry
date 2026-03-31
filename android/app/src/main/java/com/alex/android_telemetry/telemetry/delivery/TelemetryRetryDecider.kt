package com.alex.android_telemetry.telemetry.delivery

sealed interface DeliveryFailure {
    data class Network(val message: String?) : DeliveryFailure
    data class Http(val code: Int, val body: String?) : DeliveryFailure
    data class Serialization(val message: String?) : DeliveryFailure
}

sealed interface RetryDecision {
    data class Retry(val reason: String) : RetryDecision
    data class FailTerminal(val reason: String) : RetryDecision
    data class FailAuth(val reason: String) : RetryDecision
}

class TelemetryRetryDecider(
    private val policy: TelemetryDeliveryPolicy,
) {
    fun decide(failure: DeliveryFailure, attemptCount: Int): RetryDecision {
        if (attemptCount >= policy.maxAttempts) {
            return RetryDecision.FailTerminal("max attempts reached")
        }

        return when (failure) {
            is DeliveryFailure.Network -> RetryDecision.Retry(failure.message ?: "network")
            is DeliveryFailure.Serialization -> RetryDecision.FailTerminal(
                failure.message ?: "serialization"
            )
            is DeliveryFailure.Http -> when (failure.code) {
                400, 404, 409, 422 -> RetryDecision.FailTerminal("http ${failure.code}")
                401, 403 -> RetryDecision.FailAuth("http ${failure.code}")
                408, 425, 429 -> RetryDecision.Retry("http ${failure.code}")
                in 500..599 -> RetryDecision.Retry("http ${failure.code}")
                else -> RetryDecision.FailTerminal("http ${failure.code}")
            }
        }
    }
}