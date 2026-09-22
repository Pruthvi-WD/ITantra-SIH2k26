package com.example.ml.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.domain.model.Language
import java.util.Locale
import java.util.UUID

/**
 * Production-grade Offline Text-To-Speech Engine using Android's native TTS system.
 * Features:
 *  - Tailored natural voices for Hindi and English (Indic natural vs tactical styles)
 *  - Tone-adaptive sentence prosody: inflects pitch and rate dynamically according to sentence tone
 *    (Emergency / Alert, Inquiry / Question, Reassurance / Safe, Tactical Instruction, Natural Statement)
 *  - Utterance chaining across multi-sentence messages
 */
class OfflineTextToSpeechEngine(
    private val context: Context
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "OfflineTTS"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: Language = Language.HINDI
    private var voiceStyle: VoiceStyle = VoiceStyle.NATURAL_WARM
    private var toneModulationEnabled: Boolean = true
    private val mainHandler = Handler(Looper.getMainLooper())

    // Active callback tracking by utterance ID
    private val activeCallbacks = mutableMapOf<String, Pair<() -> Unit, () -> Unit>>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { id ->
                        mainHandler.post {
                            activeCallbacks[id]?.first?.invoke()
                        }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id ->
                        mainHandler.post {
                            activeCallbacks.remove(id)?.second?.invoke()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { id ->
                        mainHandler.post {
                            activeCallbacks.remove(id)?.second?.invoke()
                        }
                    }
                }
            })
            applyLanguageAndVoice(currentLanguage)
        } else {
            Log.e(TAG, "Failed to initialize native TextToSpeech engine")
        }
    }

    override suspend fun initialize(language: Language) {
        currentLanguage = language
        if (isInitialized) {
            applyLanguageAndVoice(language)
        }
    }

    override fun setLanguage(language: Language) {
        currentLanguage = language
        if (isInitialized) {
            applyLanguageAndVoice(language)
        }
    }

    override fun setVoiceStyle(style: VoiceStyle) {
        voiceStyle = style
        if (isInitialized) {
            applyLanguageAndVoice(currentLanguage)
        }
    }

    override fun getVoiceStyle(): VoiceStyle = voiceStyle

    override fun setToneModulationEnabled(enabled: Boolean) {
        toneModulationEnabled = enabled
    }

    override fun isToneModulationEnabled(): Boolean = toneModulationEnabled

    /**
     * Applies locale and selects the highest-quality, expressive voice for Hindi & English.
     */
    private fun applyLanguageAndVoice(language: Language) {
        val engine = tts ?: return
        try {
            val result = engine.setLanguage(language.locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale ${language.locale} missing or not supported, trying default")
            }

            // Attempt to select specific natural voice for Hindi and English
            val chosenVoice = findBestVoice(engine, language, voiceStyle)
            if (chosenVoice != null) {
                try {
                    engine.voice = chosenVoice
                    Log.i(TAG, "Applied voice: ${chosenVoice.name} for ${language.code} (${voiceStyle.name})")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set specific voice, using default locale voice", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring TTS language and voice", e)
        }
    }

    /**
     * Finds the best matching voice for Hindi, English, and other Indic languages.
     */
    private fun findBestVoice(engine: TextToSpeech, language: Language, style: VoiceStyle): Voice? {
        val voices = try {
            engine.voices
        } catch (e: Exception) {
            null
        } ?: return null

        val langCode = language.code.take(2).lowercase() // "hi", "en", etc.

        // Filter voices for this language
        val matchingVoices = voices.filter { voice ->
            voice.locale.language.equals(langCode, ignoreCase = true)
        }

        if (matchingVoices.isEmpty()) return null

        // 1. Hindi Voice Specialization
        if (langCode == "hi") {
            return when (style) {
                VoiceStyle.NATURAL_WARM -> {
                    // Google TTS natural female / warm voices: "hi-in-x-hie-local", "hi-in-x-hia-local"
                    matchingVoices.firstOrNull { it.name.contains("hie", ignoreCase = true) }
                        ?: matchingVoices.firstOrNull { it.name.contains("hia", ignoreCase = true) }
                        ?: matchingVoices.firstOrNull { !it.isNetworkConnectionRequired && it.name.contains("local", ignoreCase = true) }
                        ?: matchingVoices.firstOrNull { !it.isNetworkConnectionRequired }
                        ?: matchingVoices.first()
                }
                VoiceStyle.TACTICAL_COMMAND -> {
                    // Clear, direct voice: "hi-in-x-hid-local", "hi-in-x-hic-local"
                    matchingVoices.firstOrNull { it.name.contains("hid", ignoreCase = true) }
                        ?: matchingVoices.firstOrNull { it.name.contains("hic", ignoreCase = true) }
                        ?: matchingVoices.firstOrNull { !it.isNetworkConnectionRequired }
                        ?: matchingVoices.first()
                }
            }
        }

        // 2. English Voice Specialization (prefer natural Indian English or expressive local voices)
        if (langCode == "en") {
            val indianEnglish = matchingVoices.filter { it.locale.country.equals("IN", ignoreCase = true) }
            val pool = if (indianEnglish.isNotEmpty()) indianEnglish else matchingVoices

            return when (style) {
                VoiceStyle.NATURAL_WARM -> {
                    // Expressive warm English voice
                    pool.firstOrNull { it.name.contains("ene", ignoreCase = true) }
                        ?: pool.firstOrNull { it.name.contains("sfg", ignoreCase = true) }
                        ?: pool.firstOrNull { !it.isNetworkConnectionRequired && it.name.contains("local", ignoreCase = true) }
                        ?: pool.firstOrNull { !it.isNetworkConnectionRequired }
                        ?: pool.first()
                }
                VoiceStyle.TACTICAL_COMMAND -> {
                    // Clear tactical communications voice
                    pool.firstOrNull { it.name.contains("enc", ignoreCase = true) }
                        ?: pool.firstOrNull { it.name.contains("end", ignoreCase = true) }
                        ?: pool.firstOrNull { it.name.contains("rjs", ignoreCase = true) }
                        ?: pool.firstOrNull { !it.isNetworkConnectionRequired }
                        ?: pool.first()
                }
            }
        }

        // For other languages: pick highest quality offline voice
        return matchingVoices.firstOrNull { !it.isNetworkConnectionRequired } ?: matchingVoices.first()
    }

    /**
     * Sentence tone classification to make the voice speak in the natural tone of the sentence.
     */
    enum class SentenceTone(
        val pitchMultiplier: Float,
        val rateMultiplier: Float
    ) {
        URGENT_DISTRESS(1.22f, 1.12f),       // Urgent emergency, higher pitch, fast cadence
        INQUIRY_QUESTION(1.12f, 0.95f),      // Question inflection, slightly elevated pitch, deliberate
        REASSURING_SAFE(0.94f, 0.96f),       // Reassuring, calm, warm lower pitch, steady
        TACTICAL_INSTRUCTION(1.04f, 1.02f),  // Directive command, crisp, clear
        NATURAL_CONVERSATION(1.02f, 0.98f)   // Natural human speech rate & pitch
    }

    private fun analyzeSentenceTone(text: String, isAlert: Boolean): SentenceTone {
        if (isAlert) return SentenceTone.URGENT_DISTRESS

        val trimmed = text.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check for Emergency / Distress / Warning
        val alertKeywords = listOf(
            "emergency", "alert", "danger", "help", "sos", "mayday", "hazard", "fire",
            "flood", "evacuate", "trapped", "critical", "urgent", "fast", "quick", "warning",
            "खतरा", "मदद", "आपातकाल", "बचाओ", "सावधान", "तुरंत", "जल्दी", "सुरक्षित नहीं",
            "संकट", "துயரம்", "ஆபத்து", "సహాయం", "ప్రమాదం", "ತುರ್ತು", "ಆಪತ್ತು"
        )
        if (trimmed.endsWith("!") || alertKeywords.any { lower.contains(it) }) {
            return SentenceTone.URGENT_DISTRESS
        }

        // 2. Check for Inquiry / Question
        val questionKeywords = listOf(
            "what", "where", "who", "when", "why", "how", "is there", "are you",
            "do you copy", "copy?", "any update", "status", "can you hear", "location",
            "क्या", "कहाँ", "कैसे", "कब", "कौन", "किधर", "कोई है", "कॉपी", "सुनाई दे रहा",
            "ఎక్కడ", "ఏమిటి", "ಎಲ್ಲಿ", "ಎಷ್ಟು", "எங்கே", "என்ன", "എവിടെ"
        )
        if (trimmed.endsWith("?") || questionKeywords.any { lower.contains(it) }) {
            return SentenceTone.INQUIRY_QUESTION
        }

        // 3. Check for Calm / Reassurance / Confirmation
        val calmKeywords = listOf(
            "safe", "all clear", "reached", "secured", "roger", "copy that", "affirmative",
            "received", "understood", "good", "fine", "no problem", "all good", "thank",
            "सुरक्षित", "पहुँच गए", "सब ठीक", "क्लियर", "हाँ", "धन्यवाद", "प्राप्त", "ठीक है",
            "సురక్షితం", "భద్రం", "ಸುರಕ್ಷಿತ", "பாதுகாப்பானது", "നല്ലത്"
        )
        if (calmKeywords.any { lower.contains(it) }) {
            return SentenceTone.REASSURING_SAFE
        }

        // 4. Tactical instruction / Command
        val instructionKeywords = listOf(
            "move", "head to", "proceed", "turn", "stop", "hold position", "stand by", "report to",
            "आगे बढ़ो", "रुकें", "स्थान पर जाओ", "प्रतीक्षा करें", "सूचना दें"
        )
        if (instructionKeywords.any { lower.contains(it) }) {
            return SentenceTone.TACTICAL_INSTRUCTION
        }

        return SentenceTone.NATURAL_CONVERSATION
    }

    private fun splitIntoSentences(text: String): List<String> {
        val raw = text.split(Regex("(?<=[.!?\n|।])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (raw.isEmpty()) listOf(text.trim()) else raw
    }

    override fun synthesizeAndPlay(
        text: String,
        isAlert: Boolean,
        onStarted: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val engine = tts
        if (!isInitialized || engine == null) {
            onError("TTS engine not yet initialized")
            onDone()
            return
        }

        if (text.isBlank()) {
            onDone()
            return
        }

        val sentences = if (toneModulationEnabled) splitIntoSentences(text) else listOf(text)
        val initialQueueMode = if (isAlert) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val masterId = UUID.randomUUID().toString()

        var startedFired = false
        val totalSentences = sentences.size

        sentences.forEachIndexed { index, sentence ->
            val utteranceId = "$masterId-$index"
            val isFirst = (index == 0)
            val isLast = (index == totalSentences - 1)

            activeCallbacks[utteranceId] = Pair(
                {
                    if (isFirst && !startedFired) {
                        startedFired = true
                        onStarted()
                    }
                },
                {
                    if (isLast) {
                        onDone()
                    }
                }
            )

            // Calculate sentence tone prosody
            val tone = if (toneModulationEnabled) {
                analyzeSentenceTone(sentence, isAlert)
            } else {
                if (isAlert) SentenceTone.URGENT_DISTRESS else SentenceTone.NATURAL_CONVERSATION
            }

            val basePitch = if (voiceStyle == VoiceStyle.NATURAL_WARM) 1.04f else 0.98f
            val baseRate = 0.98f

            engine.setPitch((basePitch * tone.pitchMultiplier).coerceIn(0.6f, 2.0f))
            engine.setSpeechRate((baseRate * tone.rateMultiplier).coerceIn(0.6f, 2.0f))

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            val queueMode = if (isFirst) initialQueueMode else TextToSpeech.QUEUE_ADD
            val result = engine.speak(sentence, queueMode, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                activeCallbacks.remove(utteranceId)
                if (isLast) {
                    onError("TTS speak failed with code $result")
                    onDone()
                }
            }
        }
    }

    override fun stop() {
        tts?.stop()
        activeCallbacks.clear()
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    override fun isLanguageAvailable(language: Language): Boolean {
        return tts?.let {
            val res = it.isLanguageAvailable(language.locale)
            res >= TextToSpeech.LANG_AVAILABLE
        } ?: false
    }
}
