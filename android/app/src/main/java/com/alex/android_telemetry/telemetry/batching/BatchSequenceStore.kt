package com.alex.android_telemetry.telemetry.batching

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BatchSequenceStore {
    suspend fun next(sessionId: String): Int
    suspend fun reset(sessionId: String)
    suspend fun seed(sessionId: String, nextValue: Int)
}

class InMemoryBatchSequenceStore : BatchSequenceStore {
    private val mutex = Mutex()
    private val values = mutableMapOf<String, Int>()

    override suspend fun next(sessionId: String): Int = mutex.withLock {
        val next = values[sessionId] ?: 1
        values[sessionId] = next + 1
        next
    }

    override suspend fun reset(sessionId: String) { mutex.withLock { values.remove(sessionId) } }
    override suspend fun seed(sessionId: String, nextValue: Int) { mutex.withLock { values[sessionId] = nextValue } }
}
