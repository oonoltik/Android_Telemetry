package com.alex.android_telemetry.core.id

import java.util.UUID

interface SessionIdFactory {
    fun create(): String
}

class DefaultSessionIdFactory : SessionIdFactory {
    override fun create(): String = UUID.randomUUID().toString()
}
