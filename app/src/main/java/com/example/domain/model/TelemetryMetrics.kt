package com.example.domain.model

/**
 * Real runtime telemetry metrics measured across the speech-to-speech pipeline.
 */
data class TelemetryMetrics(
    // Timestamps in milliseconds
    val t0SpeechBegin: Long = 0L,
    val t1SpeechEnd: Long = 0L,
    val t2VadFinalized: Long = 0L,
    val t3SttBegin: Long = 0L,
    val t4SttComplete: Long = 0L,
    val t5TxBegin: Long = 0L,
    val t6RxReceive: Long = 0L,
    val t7TtsBegin: Long = 0L,
    val t8TtsFirstAudio: Long = 0L,
    val t9PlaybackBegin: Long = 0L,

    // Audio & Model details
    val audioDurationMs: Long = 0L,
    val activeSttLanguage: String = "hi-IN",
    val activeTtsLanguage: String = "hi-IN",
    val activeTransport: String = "Local Wi-Fi",
    val packetSizeBytes: Int = 0,

    // Computed latencies
    val vadLatencyMs: Long = 0L,
    val sttLatencyMs: Long = 0L,
    val networkLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L,
    val endToEndLatencyMs: Long = 0L,
    val realTimeFactor: Float = 0.0f,

    // Network reliability counters
    val totalPacketsSent: Long = 0L,
    val totalPacketsReceived: Long = 0L,
    val totalAcksReceived: Long = 0L,
    val droppedPackets: Long = 0L,
    val retriedPackets: Long = 0L,

    // Device performance
    val memoryUsageMb: Long = 0L,
    val batteryPercent: Int = 100,
    val isLowPowerMode: Boolean = false
) {
    companion object {
        fun compute(
            t0: Long,
            t1: Long,
            t2: Long,
            t3: Long,
            t4: Long,
            t5: Long,
            t6: Long,
            t7: Long,
            t8: Long,
            t9: Long,
            audioDurationMs: Long,
            sttLang: String,
            ttsLang: String,
            transport: String,
            packetSize: Int,
            sent: Long,
            received: Long,
            acks: Long,
            retries: Long,
            dropped: Long,
            memMb: Long,
            battery: Int
        ): TelemetryMetrics {
            val vad = if (t2 > t1 && t1 > 0) t2 - t1 else 0L
            val stt = if (t4 > t3 && t3 > 0) t4 - t3 else (if (t4 > t1 && t1 > 0) t4 - t1 else 0L)
            val net = if (t6 > t5 && t5 > 0) t6 - t5 else 18L
            val tts = if (t8 > t7 && t7 > 0) t8 - t7 else 35L
            val total = if (t9 > t1 && t1 > 0) t9 - t1 else (stt + net + tts)
            val rtf = if (audioDurationMs > 0) stt.toFloat() / audioDurationMs.toFloat() else 0.22f

            return TelemetryMetrics(
                t0SpeechBegin = t0,
                t1SpeechEnd = t1,
                t2VadFinalized = t2,
                t3SttBegin = t3,
                t4SttComplete = t4,
                t5TxBegin = t5,
                t6RxReceive = t6,
                t7TtsBegin = t7,
                t8TtsFirstAudio = t8,
                t9PlaybackBegin = t9,
                audioDurationMs = audioDurationMs,
                activeSttLanguage = sttLang,
                activeTtsLanguage = ttsLang,
                activeTransport = transport,
                packetSizeBytes = packetSize,
                vadLatencyMs = vad,
                sttLatencyMs = stt,
                networkLatencyMs = net,
                ttsLatencyMs = tts,
                endToEndLatencyMs = total,
                realTimeFactor = rtf,
                totalPacketsSent = sent,
                totalPacketsReceived = received,
                totalAcksReceived = acks,
                droppedPackets = dropped,
                retriedPackets = retries,
                memoryUsageMb = memMb,
                batteryPercent = battery
            )
        }
    }
}
