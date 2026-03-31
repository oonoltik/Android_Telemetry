package com.alex.android_telemetry.telemetry.batching

import com.alex.android_telemetry.telemetry.model.TelemetrySampleDraft

class TelemetryBatchWindow {
    private val samples = mutableListOf<TelemetrySampleDraft>()
    fun append(sample: TelemetrySampleDraft) { samples += sample }
    fun appendAll(samples: List<TelemetrySampleDraft>) { this.samples += samples }
    fun size(): Int = samples.size
    fun snapshot(): List<TelemetrySampleDraft> = samples.toList()
    fun drain(): List<TelemetrySampleDraft> = samples.toList().also { samples.clear() }
    fun clear() { samples.clear() }
}
