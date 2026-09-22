package com.example.ml.stt

import com.example.domain.model.Language

interface SpeechToTextEngine {
    suspend fun initialize(language: Language)
    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String, Long) -> Unit, // text, inferenceLatencyMs
        onError: (String) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null
    )
    fun stopListening()
    fun release()
    fun isAvailable(): Boolean
}
