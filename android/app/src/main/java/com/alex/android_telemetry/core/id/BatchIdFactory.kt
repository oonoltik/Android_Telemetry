package com.alex.android_telemetry.core.id

import java.util.UUID

interface BatchIdFactory {
    fun create(sessionId: String, batchSeq: Int): String
}

class DefaultBatchIdFactory : BatchIdFactory {
    override fun create(sessionId: String, batchSeq: Int): String = "${sessionId}_${batchSeq}_${UUID.randomUUID()}"
}
