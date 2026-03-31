package com.alex.android_telemetry.telemetry.session

interface TripSessionRepository {
    suspend fun getActiveSession(): TripSession?
    suspend fun saveActiveSession(session: TripSession)
    suspend fun updateNextBatchSeq(sessionId: String, nextBatchSeq: Int)
    suspend fun clearActiveSession()
}
