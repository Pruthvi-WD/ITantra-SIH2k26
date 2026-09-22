package com.example.audio

import kotlin.math.abs

/**
 * Real-time Voice Activity Detector (VAD) optimized for low CPU/battery usage.
 * Uses Short-Time Energy (STE) and Zero Crossing Rate (ZCR) with adaptive background noise calibration.
 */
class VoiceActivityDetector(
    private var sensitivity: Float = 0.5f, // 0.1 (least sensitive) to 1.0 (most sensitive)
    private var pauseThresholdMs: Long = 850L // Pause duration to trigger sentence finalization
) {
    enum class VadState {
        SILENCE,
        SPEECH_DETECTED,
        PAUSE_DETECTED
    }

    private var noiseFloorEnergy = 120.0
    private var speechStartTime = 0L
    private var lastSpeechTime = 0L
    private var isSpeaking = false
    private var calibrationFrames = 0
    private val calibrationMaxFrames = 30

    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechEnded: ((durationMs: Long) -> Unit)? = null

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.1f, 1.0f)
    }

    fun setPauseThreshold(thresholdMs: Long) {
        pauseThresholdMs = thresholdMs.coerceIn(400L, 2500L)
    }

    /**
     * Process 16-bit PCM chunk (16kHz).
     */
    fun processChunk(pcmBuffer: ShortArray, length: Int): VadState {
        if (length <= 0) return VadState.SILENCE

        // Compute Short-Time Energy (STE) and Zero-Crossing Rate (ZCR)
        var totalEnergy = 0.0
        var zeroCrossings = 0

        for (i in 0 until length) {
            val sample = abs(pcmBuffer[i].toInt())
            totalEnergy += sample

            if (i > 0 && ((pcmBuffer[i] >= 0 && pcmBuffer[i - 1] < 0) || (pcmBuffer[i] < 0 && pcmBuffer[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        val frameEnergy = totalEnergy / length
        val zcr = zeroCrossings.toDouble() / length

        // Dynamic background noise calibration during initial silence
        if (calibrationFrames < calibrationMaxFrames) {
            calibrationFrames++
            noiseFloorEnergy = (noiseFloorEnergy * 0.9) + (frameEnergy * 0.1)
            return VadState.SILENCE
        }

        // Adaptive speech energy threshold based on user sensitivity setting
        val multiplier = 2.0 + (1.0 - sensitivity) * 4.0 // 2.0x to 6.0x noise floor
        val speechThreshold = maxOf(noiseFloorEnergy * multiplier, 350.0)

        val isVoiceFrame = frameEnergy > speechThreshold && zcr in 0.02..0.65
        val currentTime = System.currentTimeMillis()

        if (isVoiceFrame) {
            lastSpeechTime = currentTime
            if (!isSpeaking) {
                isSpeaking = true
                speechStartTime = currentTime
                onSpeechStarted?.invoke()
            }
            return VadState.SPEECH_DETECTED
        } else {
            // Update noise floor slowly during non-voice frames
            noiseFloorEnergy = (noiseFloorEnergy * 0.98) + (frameEnergy * 0.02)

            if (isSpeaking) {
                val silenceDuration = currentTime - lastSpeechTime
                if (silenceDuration >= pauseThresholdMs) {
                    isSpeaking = false
                    val totalSpeechDuration = lastSpeechTime - speechStartTime
                    onSpeechEnded?.invoke(maxOf(300L, totalSpeechDuration))
                    return VadState.PAUSE_DETECTED
                }
                return VadState.SPEECH_DETECTED // In-between word pause
            }
            return VadState.SILENCE
        }
    }

    fun reset() {
        isSpeaking = false
        speechStartTime = 0L
        lastSpeechTime = 0L
    }
}
