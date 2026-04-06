package com.alex.android_telemetry.sensors.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.alex.android_telemetry.sensors.api.ScreenInteractionSource
import com.alex.android_telemetry.telemetry.domain.model.ScreenInteractionSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class AndroidScreenInteractionSource(
    private val context: Context,
) : ScreenInteractionSource {

    private val mutableSamples = MutableSharedFlow<ScreenInteractionSample>(extraBufferCapacity = 64)
    override val samples: Flow<ScreenInteractionSample> = mutableSamples.asSharedFlow().distinctUntilChanged()

    private var receiver: BroadcastReceiver? = null
    private var started = false
    private var activeStartedAt: Instant? = null

    override suspend fun start() {
        if (started) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        val localReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val now = Clock.System.now()
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT -> {
                        if (activeStartedAt == null) {
                            activeStartedAt = now
                        }
                        mutableSamples.tryEmit(
                            ScreenInteractionSample(
                                timestamp = now,
                                activeStartedAt = activeStartedAt,
                                activeEndedAt = null,
                            )
                        )
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        mutableSamples.tryEmit(
                            ScreenInteractionSample(
                                timestamp = now,
                                activeStartedAt = activeStartedAt,
                                activeEndedAt = now,
                            )
                        )
                        activeStartedAt = null
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                localReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(localReceiver, filter)
        }

        receiver = localReceiver
        started = true
    }

    override suspend fun stop() {
        if (!started) return
        val localReceiver = receiver
        if (localReceiver != null) {
            runCatching { context.unregisterReceiver(localReceiver) }
        }
        receiver = null
        activeStartedAt = null
        started = false
    }
}