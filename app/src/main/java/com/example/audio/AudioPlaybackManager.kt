package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages audio focus, speaker routing, haptic feedback, and distress tone alerts.
 */
class AudioPlaybackManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.w("AudioPlayback", "ToneGenerator init failed", e)
        }
    }

    /**
     * Request audio focus before playing speech.
     */
    fun requestAudioFocus(isAlert: Boolean = false): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val usage = if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                val contentType = AudioAttributes.CONTENT_TYPE_SPEECH

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                val streamType = if (isAlert) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    streamType,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Raise volume to maximum appropriate level for distress / alert messages.
     */
    fun elevateAlertVolume() {
        try {
            val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxMusicVol * 0.95).toInt(), 0)
        } catch (e: Exception) {
            Log.w("AudioPlayback", "Unable to elevate volume", e)
        }
    }

    /**
     * Play tactical tone for alert notification.
     */
    fun playAlertChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
        } catch (e: Exception) {
            Log.w("AudioPlayback", "Failed to play chime", e)
        }
    }

    /**
     * Haptic feedback patterns for PTT start, stop, send, and alert.
     */
    fun vibrate(type: HapticType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                when (type) {
                    HapticType.PTT_PRESS -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    HapticType.PTT_RELEASE -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    HapticType.ALERT_RECEIVED -> vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 300), -1)
                    )
                    HapticType.MESSAGE_DELIVERED -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                when (type) {
                    HapticType.PTT_PRESS -> vibrator?.vibrate(35)
                    HapticType.PTT_RELEASE -> vibrator?.vibrate(20)
                    HapticType.ALERT_RECEIVED -> vibrator?.vibrate(longArrayOf(0, 200, 100, 200, 100, 300), -1)
                    HapticType.MESSAGE_DELIVERED -> vibrator?.vibrate(longArrayOf(0, 40, 50, 40), -1)
                }
            }
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    enum class HapticType {
        PTT_PRESS,
        PTT_RELEASE,
        ALERT_RECEIVED,
        MESSAGE_DELIVERED
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
