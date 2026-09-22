package com.example.ml.tts

import com.example.domain.model.Language

enum class VoiceStyle(val id: String, val title: String, val desc: String) {
    NATURAL_WARM("natural_warm", "Natural Warm Voice", "Expressive human cadence with natural sentence inflection"),
    TACTICAL_COMMAND("tactical_command", "Tactical Radio Voice", "Crisp, authoritative, high-clarity communications voice")
}

interface TextToSpeechEngine {
    suspend fun initialize(language: Language)
    fun setLanguage(language: Language)
    fun setVoiceStyle(style: VoiceStyle)
    fun getVoiceStyle(): VoiceStyle
    fun setToneModulationEnabled(enabled: Boolean)
    fun isToneModulationEnabled(): Boolean
    fun synthesizeAndPlay(
        text: String,
        isAlert: Boolean = false,
        onStarted: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )
    fun stop()
    fun release()
    fun isLanguageAvailable(language: Language): Boolean
}
