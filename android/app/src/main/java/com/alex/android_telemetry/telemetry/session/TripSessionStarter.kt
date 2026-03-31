package com.alex.android_telemetry.telemetry.session

import com.alex.android_telemetry.core.id.SessionIdFactory
import com.alex.android_telemetry.core.time.ClockProvider
import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode

class TripSessionStarter(
    private val sessionIdFactory: SessionIdFactory,
    private val clockProvider: ClockProvider,
    private val sessionRepository: TripSessionRepository
) {
    suspend fun start(deviceId: String, driverId: String?, trackingMode: TrackingMode, transportMode: TransportMode): TripSession {
        val existing = sessionRepository.getActiveSession()
        if (existing != null) return existing

        val session = TripSession(
            sessionId = sessionIdFactory.create(),
            deviceId = deviceId,
            driverId = driverId,
            trackingMode = trackingMode,
            transportMode = transportMode,
            startedAt = clockProvider.nowIsoStringUtc(),
            startedAtEpochMillis = clockProvider.nowEpochMillis(),
            nextBatchSeq = 1,
            isActive = true
        )
        sessionRepository.saveActiveSession(session)
        return session
    }
}
