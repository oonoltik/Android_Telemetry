package com.alex.android_telemetry.telemetry.storage.runtime

import com.alex.android_telemetry.telemetry.domain.TrackingMode
import com.alex.android_telemetry.telemetry.domain.TransportMode
import com.alex.android_telemetry.telemetry.session.TripSession

class RuntimeStateMapper {
    fun toEntity(session: TripSession): ActiveTripEntity = ActiveTripEntity(
        sessionId = session.sessionId,
        deviceId = session.deviceId,
        driverId = session.driverId,
        trackingMode = session.trackingMode.name,
        transportMode = session.transportMode.name,
        startedAt = session.startedAt,
        startedAtEpochMillis = session.startedAtEpochMillis,
        nextBatchSeq = session.nextBatchSeq,
        isActive = session.isActive
    )

    fun toDomain(entity: ActiveTripEntity): TripSession = TripSession(
        sessionId = entity.sessionId,
        deviceId = entity.deviceId,
        driverId = entity.driverId,
        trackingMode = runCatching { TrackingMode.valueOf(entity.trackingMode) }
            .getOrDefault(TrackingMode.SINGLE_TRIP),
        transportMode = runCatching { TransportMode.valueOf(entity.transportMode) }
            .getOrDefault(TransportMode.UNKNOWN),
        startedAt = entity.startedAt,
        startedAtEpochMillis = entity.startedAtEpochMillis,
        nextBatchSeq = entity.nextBatchSeq,
        isActive = entity.isActive
    )
}