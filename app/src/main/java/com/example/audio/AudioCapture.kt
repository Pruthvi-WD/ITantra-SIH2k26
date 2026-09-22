package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Low-latency 16 kHz Mono 16-bit PCM Audio Capture using AudioRecord.
 * Reuses internal buffers to prevent memory allocation and garbage collection churn.
 */
class AudioCapture(
    private val onPcmChunk: (ShortArray, Int) -> Unit
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 640 // 40ms of audio at 16kHz
        private const val TAG = "AudioCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    // Circular waveform history for Compose canvas
    private val _waveformSamples = MutableStateFlow(FloatArray(32) { 0.05f })
    val waveformSamples: StateFlow<FloatArray> = _waveformSamples.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_isRecording.value) return true

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = maxOf(minBufferSize, CHUNK_SIZE * 4)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record
            _isRecording.value = true

            recordingJob = scope.launch {
                val pcmBuffer = ShortArray(CHUNK_SIZE)
                val waveformBuffer = FloatArray(32) { 0.05f }
                var bufferIndex = 0

                while (isActive && _isRecording.value) {
                    val readSamples = record.read(pcmBuffer, 0, CHUNK_SIZE)
                    if (readSamples > 0) {
                        // Calculate RMS Amplitude
                        var sum = 0.0
                        for (i in 0 until readSamples) {
                            val sample = pcmBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / readSamples)
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        _amplitude.value = normalized

                        // Update waveform history
                        waveformBuffer[bufferIndex] = (normalized * 2.5f).coerceIn(0.08f, 1f)
                        bufferIndex = (bufferIndex + 1) % 32
                        _waveformSamples.value = waveformBuffer.copyOf()

                        // Dispatch to VAD & STT listeners
                        onPcmChunk(pcmBuffer, readSamples)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio record", e)
            _isRecording.value = false
            return false
        }
    }

    fun updateWaveformExternally(normalized: Float) {
        val current = _waveformSamples.value.copyOf()
        for (i in 0 until current.size - 1) {
            current[i] = current[i + 1]
        }
        current[current.size - 1] = (normalized * 2.2f).coerceIn(0.08f, 1f)
        _waveformSamples.value = current
        _amplitude.value = normalized
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        } finally {
            audioRecord = null
            _amplitude.value = 0f
            _waveformSamples.value = FloatArray(32) { 0.05f }
        }
    }
}
