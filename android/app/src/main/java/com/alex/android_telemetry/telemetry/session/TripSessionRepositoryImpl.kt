package com.alex.android_telemetry.telemetry.session

import com.alex.android_telemetry.telemetry.storage.runtime.ActiveTripDao
import com.alex.android_telemetry.telemetry.storage.runtime.RuntimeStateMapper

class TripSessionRepositoryImpl(
    private val activeTripDao: ActiveTripDao,
    private val mapper: RuntimeStateMapper
) : TripSessionRepository {
    override suspend fun getActiveSession(): TripSession? = activeTripDao.getActiveTrip()?.let(mapper::toDomain)
    override suspend fun saveActiveSession(session: TripSession) { activeTripDao.upsert(mapper.toEntity(session)) }
    override suspend fun updateNextBatchSeq(sessionId: String, nextBatchSeq: Int) {
        val current = getActiveSession() ?: return
        if (current.sessionId != sessionId) return
        saveActiveSession(current.copy(nextBatchSeq = nextBatchSeq))
    }
    override suspend fun clearActiveSession() { activeTripDao.clear() }
}
