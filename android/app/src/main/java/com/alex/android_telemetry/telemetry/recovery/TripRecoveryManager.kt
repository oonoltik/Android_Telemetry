package com.alex.android_telemetry.telemetry.recovery

import com.alex.android_telemetry.core.dispatchers.AppDispatchers
import com.alex.android_telemetry.core.log.TelemetryLogger
import com.alex.android_telemetry.telemetry.session.TripSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripRecoveryManager(
    private val logger: TelemetryLogger,
    private val dispatchers: AppDispatchers,
    private val tripSessionRepository: TripSessionRepository,
    private val recoverActiveTripUseCase: RecoverActiveTripUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    fun onAppStarted() {
        scope.launch {
            val active = tripSessionRepository.getActiveSession()
            if (active != null) {
                logger.i("TripRecoveryManager", "recovering session ${active.sessionId}")
                recoverActiveTripUseCase()
            }
        }
    }
}
