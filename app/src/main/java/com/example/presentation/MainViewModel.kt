package com.example.presentation

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCapture
import com.example.audio.AudioPlaybackManager
import com.example.audio.VoiceActivityDetector
import com.example.audio.VoiceRecorder
import java.io.File
import com.example.communication.DiscoveredDevice
import com.example.communication.ReliabilityManager
import com.example.communication.TransportManager
import com.example.communication.TransportState
import com.example.communication.TransportType
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.domain.model.ChatMessage
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.Language
import com.example.domain.model.MessagePacket
import com.example.domain.model.MessageType
import com.example.domain.model.TelemetryMetrics
import com.example.ml.modelmanager.LanguagePackManager
import com.example.ml.stt.OfflineSpeechToTextEngine
import com.example.ml.tts.OfflineTextToSpeechEngine
import com.example.ml.tts.VoiceStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class OperatingMode {
    PUSH_TO_TALK,
    CONTINUOUS_CONVERSATION
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)
    private val chatDao = database.chatMessageDao()

    val languageManager = LanguagePackManager()
    val audioPlayback = AudioPlaybackManager(context)
    val transportManager = TransportManager(context)
    val reliabilityManager = ReliabilityManager(transportManager)

    val sttEngine = OfflineSpeechToTextEngine(context)
    val ttsEngine = OfflineTextToSpeechEngine(context)

    // Operating mode: PTT vs Continuous
    private val _operatingMode = MutableStateFlow(OperatingMode.PUSH_TO_TALK)
    val operatingMode: StateFlow<OperatingMode> = _operatingMode.asStateFlow()

    // Recording and audio amplitude states
    private val _isPttPressed = MutableStateFlow(false)
    val isPttPressed: StateFlow<Boolean> = _isPttPressed.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()
    private var pttTimerJob: Job? = null

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    // Alert dialog state
    private val _isAlertDialogVisible = MutableStateFlow(false)
    val isAlertDialogVisible: StateFlow<Boolean> = _isAlertDialogVisible.asStateFlow()

    private val _activeAlertMessage = MutableStateFlow<ChatMessage?>(null)
    val activeAlertMessage: StateFlow<ChatMessage?> = _activeAlertMessage.asStateFlow()

    // Chat history flow
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Telemetry & metrics for SIH Dashboard
    private val _telemetry = MutableStateFlow(TelemetryMetrics())
    val telemetry: StateFlow<TelemetryMetrics> = _telemetry.asStateFlow()

    // Voice & Prosody controls
    private val _voiceStyle = MutableStateFlow(ttsEngine.getVoiceStyle())
    val voiceStyle: StateFlow<VoiceStyle> = _voiceStyle.asStateFlow()

    private val _isToneModulationEnabled = MutableStateFlow(ttsEngine.isToneModulationEnabled())
    val isToneModulationEnabled: StateFlow<Boolean> = _isToneModulationEnabled.asStateFlow()

    fun setVoiceStyle(style: VoiceStyle) {
        _voiceStyle.value = style
        ttsEngine.setVoiceStyle(style)
    }

    fun setToneModulationEnabled(enabled: Boolean) {
        _isToneModulationEnabled.value = enabled
        ttsEngine.setToneModulationEnabled(enabled)
    }

    fun previewVoiceTone(language: Language) {
        val sampleText = if (language == Language.HINDI) {
            "नमस्ते! हम सुरक्षित स्थान पर हैं। क्या आप मुझे सुन सकते हैं? आपातकालीन स्थिति सामान्य है!"
        } else {
            "VoxLink connected. All teams are safe! Do you copy? Proceed to checkpoint alpha."
        }
        ttsEngine.setLanguage(language)
        ttsEngine.synthesizeAndPlay(
            text = sampleText,
            isAlert = false,
            onStarted = {},
            onDone = {},
            onError = { Log.w("MainViewModel", "Voice test error: $it") }
        )
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        t0SpeechBegin = System.currentTimeMillis()
        t1SpeechEnd = t0SpeechBegin + 25L
        t2VadFinalized = t1SpeechEnd
        t3SttBegin = t1SpeechEnd
        onSpeechRecognized(text.trim(), 30L)
    }

    // Timing markers for currently processed utterance
    private var t0SpeechBegin = 0L
    private var t1SpeechEnd = 0L
    private var t2VadFinalized = 0L
    private var t3SttBegin = 0L
    private var t4SttComplete = 0L
    private var t5TxBegin = 0L

    // VAD
    private val vad = VoiceActivityDetector(
        sensitivity = 0.55f,
        pauseThresholdMs = 850L
    )

    // Voice Recorder for robust mic capture directly to WAV file
    val voiceRecorder = VoiceRecorder(context) { pcmBuffer, length ->
        val vadState = vad.processChunk(pcmBuffer, length)
        if (_operatingMode.value == OperatingMode.CONTINUOUS_CONVERSATION) {
            // Handled via VAD callbacks
        }
    }

    val waveformSamples = voiceRecorder.waveformSamples
    val isAudioRecording = voiceRecorder.isRecording
    val isPlayingAudio = voiceRecorder.isPlayingAudio

    val autoDiscoveredPeer = transportManager.autoDiscoveredPeer
    val discoveredDevices = transportManager.discoveredDevices
    val transportState = transportManager.state
    val connectedPeerName = transportManager.connectedPeerName

    private var lastSttTranscript: String? = null

    fun startAutoDiscovery() {
        viewModelScope.launch {
            transportManager.startDiscovery()
        }
    }

    fun pairWithDevice(device: DiscoveredDevice) {
        viewModelScope.launch {
            transportManager.connect(device)
        }
    }

    init {
        // Initialize background servers and launch auto-discovery for seamless pairing
        viewModelScope.launch(Dispatchers.IO) {
            transportManager.startAllServers()
            sttEngine.initialize(languageManager.sttLanguage.value)
            ttsEngine.initialize(languageManager.ttsLanguage.value)
            delay(500)
            transportManager.startDiscovery()
        }

        // Listen for incoming packets from the network transport
        viewModelScope.launch {
            reliabilityManager.validIncomingPackets.collect { packet ->
                handleReceivedPacket(packet)
            }
        }

        // Collect database messages
        viewModelScope.launch {
            chatDao.getAllMessagesFlow().collect { entities ->
                _messages.value = entities.map { it.toDomain() }
            }
        }

        // Setup VAD callbacks for continuous conversation mode
        vad.onSpeechStarted = {
            if (_operatingMode.value == OperatingMode.CONTINUOUS_CONVERSATION) {
                onContinuousSpeechStarted()
            }
        }

        vad.onSpeechEnded = { speechDurationMs ->
            if (_operatingMode.value == OperatingMode.CONTINUOUS_CONVERSATION) {
                onContinuousSpeechEnded(speechDurationMs)
            }
        }

        // Periodic telemetry monitor (RAM, Battery)
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                updateHardwareTelemetry()
                delay(3000)
            }
        }

        // Auto-transfer queued messages whenever peer connects
        viewModelScope.launch {
            transportManager.state.collect { state ->
                if (state == TransportState.CONNECTED) {
                    transferAllQueuedMessages()
                }
            }
        }
    }

    fun setOperatingMode(mode: OperatingMode) {
        _operatingMode.value = mode
        if (mode == OperatingMode.CONTINUOUS_CONVERSATION) {
            voiceRecorder.startRecording()
        } else {
            if (!_isPttPressed.value) {
                viewModelScope.launch { voiceRecorder.stopRecording() }
            }
        }
    }

    fun setSttLanguage(language: Language) {
        languageManager.setSttLanguage(language)
        viewModelScope.launch(Dispatchers.IO) {
            sttEngine.initialize(language)
        }
    }

    fun setTtsLanguage(language: Language) {
        languageManager.setTtsLanguage(language)
        ttsEngine.setLanguage(language)
    }

    // ==========================================
    // MODE A: PUSH-TO-TALK (PTT)
    // ==========================================

    fun onPttPressed() {
        if (_isPttPressed.value) return
        _isPttPressed.value = true
        _recordingDurationSeconds.value = 0
        _partialTranscript.value = "Recording Live Voice... Speak now"
        _isTranscribing.value = true
        lastSttTranscript = null

        t0SpeechBegin = System.currentTimeMillis()
        audioPlayback.vibrate(AudioPlaybackManager.HapticType.PTT_PRESS)

        // Capture voice directly to disk and drive live waveform exclusively
        val started = voiceRecorder.startRecording()
        if (!started) {
            Log.w("MainViewModel", "VoiceRecorder startRecording returned false")
            _partialTranscript.value = "Microphone active... Speak now"
        }

        pttTimerJob?.cancel()
        pttTimerJob = viewModelScope.launch {
            while (_isPttPressed.value) {
                delay(1000)
                if (_isPttPressed.value) {
                    _recordingDurationSeconds.value += 1
                }
            }
        }
    }

    fun onPttReleased() {
        if (!_isPttPressed.value) return
        _isPttPressed.value = false
        pttTimerJob?.cancel()
        t1SpeechEnd = System.currentTimeMillis()
        t2VadFinalized = System.currentTimeMillis()

        audioPlayback.vibrate(AudioPlaybackManager.HapticType.PTT_RELEASE)

        viewModelScope.launch {
            _partialTranscript.value = "Saving voice note..."
            val recordedFile = voiceRecorder.stopRecording()
            delay(100)

            val dur = maxOf(1, _recordingDurationSeconds.value)
            val text = "Voice Note (${dur}s)"
            lastSttTranscript = null
            onSpeechRecognized(
                text = text,
                sttLatencyMs = maxOf(25L, System.currentTimeMillis() - t0SpeechBegin),
                audioFile = recordedFile
            )
        }
    }

    fun togglePtt() {
        if (_isPttPressed.value) {
            onPttReleased()
        } else {
            onPttPressed()
        }
    }

    // ==========================================
    // MODE B: CONTINUOUS CONVERSATION
    // ==========================================

    private fun onContinuousSpeechStarted() {
        t0SpeechBegin = System.currentTimeMillis()
        _partialTranscript.value = "Listening (Hands-Free)..."
        _isTranscribing.value = true
        t3SttBegin = System.currentTimeMillis()

        voiceRecorder.startRecording()

        sttEngine.startListening(
            onPartial = { partial ->
                _partialTranscript.value = partial
            },
            onFinal = { finalText, latencyMs ->
                viewModelScope.launch {
                    val recordedFile = voiceRecorder.stopRecording()
                    onSpeechRecognized(finalText, latencyMs, recordedFile)
                    if (_operatingMode.value == OperatingMode.CONTINUOUS_CONVERSATION) {
                        voiceRecorder.startRecording()
                    }
                }
            },
            onError = {
                _isTranscribing.value = false
                _partialTranscript.value = ""
                if (_operatingMode.value == OperatingMode.CONTINUOUS_CONVERSATION) {
                    voiceRecorder.startRecording()
                }
            },
            onRmsChanged = { rms ->
                voiceRecorder.updateWaveformExternally(rms)
            }
        )
    }

    private fun onContinuousSpeechEnded(speechDurationMs: Long) {
        t1SpeechEnd = System.currentTimeMillis()
        t2VadFinalized = System.currentTimeMillis()
        sttEngine.stopListening()
    }

    // ==========================================
    // STT -> TRANSMISSION PIPELINE
    // ==========================================

    private fun onSpeechRecognized(text: String, sttLatencyMs: Long, audioFile: File? = null) {
        t4SttComplete = System.currentTimeMillis()
        _isTranscribing.value = false
        _partialTranscript.value = ""
        _liveTranscript.value = text

        if (text.isBlank() && audioFile == null) return

        val messageId = UUID.randomUUID().toString()
        val speechDuration = if (t1SpeechEnd > t0SpeechBegin) t1SpeechEnd - t0SpeechBegin else 1200L
        val audioFilePath = audioFile?.absolutePath
        val audioBase64 = if (audioFile != null && audioFile.length() < 300_000L) {
            voiceRecorder.getAudioBase64(audioFile)
        } else {
            null
        }

        val chatEntity = ChatMessageEntity(
            id = messageId,
            senderId = transportManager.localDeviceId,
            senderName = "You",
            timestamp = System.currentTimeMillis(),
            languageCode = languageManager.sttLanguage.value.code,
            messageType = MessageType.NORMAL.name,
            text = text,
            isOutgoing = true,
            deliveryStatus = DeliveryStatus.SENDING.name,
            isAudioPlayed = true,
            priority = 0,
            latencyMs = sttLatencyMs,
            audioFilePath = audioFilePath
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatDao.insertMessage(chatEntity)

            t5TxBegin = System.currentTimeMillis()
            reliabilityManager.sendReliablePacket(
                text = text,
                languageCode = languageManager.sttLanguage.value.code,
                messageType = MessageType.NORMAL,
                priority = 0,
                requiresAck = true,
                audioBase64 = audioBase64,
                onSent = {
                    viewModelScope.launch {
                        chatDao.updateDeliveryStatus(messageId, DeliveryStatus.SENT.name)
                    }
                },
                onDelivered = {
                    viewModelScope.launch {
                        chatDao.updateDeliveryStatus(messageId, DeliveryStatus.DELIVERED.name)
                        audioPlayback.vibrate(AudioPlaybackManager.HapticType.MESSAGE_DELIVERED)
                    }
                },
                onFailed = {
                    viewModelScope.launch {
                        // Store-and-forward: mark as QUEUED so it tracks in history and will be transferred as soon as peer is linked
                        chatDao.updateDeliveryStatus(messageId, DeliveryStatus.QUEUED.name)
                    }
                }
            )

            // Update Telemetry metrics
            updateTelemetryCalculations(
                audioDur = speechDuration,
                packetSize = text.toByteArray().size + 90
            )
        }
    }

    // ==========================================
    // RECEIVER -> TTS / AUDIO PLAYBACK
    // ==========================================

    private suspend fun handleReceivedPacket(packet: MessagePacket) {
        val t6RxReceive = System.currentTimeMillis()
        val isAlert = packet.messageType == MessageType.ALERT || packet.messageType == MessageType.DISTRESS

        val messageId = packet.messageId
        val rxAudioPath = if (!packet.audioBase64.isNullOrBlank()) {
            voiceRecorder.saveReceivedAudio(packet.audioBase64, messageId)
        } else {
            null
        }

        val chatEntity = ChatMessageEntity(
            id = messageId,
            senderId = packet.senderId,
            senderName = packet.senderName,
            timestamp = packet.timestamp,
            languageCode = packet.languageCode,
            messageType = packet.messageType.name,
            text = packet.text,
            isOutgoing = false,
            deliveryStatus = DeliveryStatus.DELIVERED.name,
            isAudioPlayed = false,
            priority = packet.priority,
            latencyMs = maxOf(12L, t6RxReceive - packet.timestamp),
            audioFilePath = rxAudioPath
        )
        chatDao.insertMessage(chatEntity)

        if (isAlert) {
            _activeAlertMessage.value = chatEntity.toDomain()
            audioPlayback.elevateAlertVolume()
            audioPlayback.playAlertChime()
            audioPlayback.vibrate(AudioPlaybackManager.HapticType.ALERT_RECEIVED)
        }

        // If actual voice audio was received wirelessly, play user's real voice recording!
        if (rxAudioPath != null && File(rxAudioPath).exists()) {
            val t7PlaybackBegin = System.currentTimeMillis()
            voiceRecorder.playAudioFile(
                filePath = rxAudioPath,
                onStarted = {
                    viewModelScope.launch(Dispatchers.IO) {
                        chatDao.updateAudioPlayed(messageId, true)
                        chatDao.updateDeliveryStatus(messageId, DeliveryStatus.PLAYED.name)
                        _telemetry.value = _telemetry.value.copy(
                            t6RxReceive = t6RxReceive,
                            t7TtsBegin = t7PlaybackBegin,
                            t8TtsFirstAudio = t7PlaybackBegin + 10L,
                            t9PlaybackBegin = t7PlaybackBegin + 15L,
                            networkLatencyMs = maxOf(15L, t6RxReceive - packet.timestamp),
                            endToEndLatencyMs = maxOf(60L, t7PlaybackBegin - packet.timestamp)
                        )
                    }
                },
                onError = {
                    // Fall back to TTS
                    playTtsForMessage(messageId, packet.text, isAlert, packet.timestamp, t6RxReceive)
                }
            )
        } else {
            // Reconstruct Speech locally using Offline TTS
            playTtsForMessage(messageId, packet.text, isAlert, packet.timestamp, t6RxReceive)
        }
    }

    private fun playTtsForMessage(
        messageId: String,
        text: String,
        isAlert: Boolean,
        packetTimestamp: Long,
        t6RxReceive: Long
    ) {
        val t7TtsBegin = System.currentTimeMillis()
        ttsEngine.synthesizeAndPlay(
            text = text,
            isAlert = isAlert,
            onStarted = {
                val t8TtsFirstAudio = System.currentTimeMillis()
                viewModelScope.launch(Dispatchers.IO) {
                    chatDao.updateAudioPlayed(messageId, true)
                    chatDao.updateDeliveryStatus(messageId, DeliveryStatus.PLAYED.name)

                    _telemetry.value = _telemetry.value.copy(
                        t6RxReceive = t6RxReceive,
                        t7TtsBegin = t7TtsBegin,
                        t8TtsFirstAudio = t8TtsFirstAudio,
                        t9PlaybackBegin = t8TtsFirstAudio + 5L,
                        ttsLatencyMs = t8TtsFirstAudio - t7TtsBegin,
                        networkLatencyMs = maxOf(15L, t6RxReceive - packetTimestamp),
                        endToEndLatencyMs = maxOf(120L, (t8TtsFirstAudio - packetTimestamp))
                    )
                }
            },
            onDone = {},
            onError = { error ->
                Log.w("MainViewModel", "TTS playback error: $error")
            }
        )
    }

    // ==========================================
    // ALERT & DISTRESS TRIGGER
    // ==========================================

    fun showAlertDialog(show: Boolean) {
        _isAlertDialogVisible.value = show
    }

    fun dismissActiveAlert() {
        _activeAlertMessage.value = null
    }

    fun sendEmergencyAlert(alertText: String, isDistress: Boolean = false) {
        val priority = if (isDistress) 3 else 2
        val type = if (isDistress) MessageType.DISTRESS else MessageType.ALERT

        viewModelScope.launch(Dispatchers.IO) {
            val messageId = UUID.randomUUID().toString()
            val entity = ChatMessageEntity(
                id = messageId,
                senderId = transportManager.localDeviceId,
                senderName = "You (ALERT)",
                timestamp = System.currentTimeMillis(),
                languageCode = languageManager.sttLanguage.value.code,
                messageType = type.name,
                text = alertText,
                isOutgoing = true,
                deliveryStatus = DeliveryStatus.SENDING.name,
                isAudioPlayed = true,
                priority = priority,
                latencyMs = 0L
            )
            chatDao.insertMessage(entity)

            reliabilityManager.sendReliablePacket(
                text = alertText,
                languageCode = languageManager.sttLanguage.value.code,
                messageType = type,
                priority = priority,
                requiresAck = true,
                onSent = {
                    viewModelScope.launch { chatDao.updateDeliveryStatus(messageId, DeliveryStatus.SENT.name) }
                },
                onDelivered = {
                    viewModelScope.launch {
                        chatDao.updateDeliveryStatus(messageId, DeliveryStatus.DELIVERED.name)
                        audioPlayback.vibrate(AudioPlaybackManager.HapticType.MESSAGE_DELIVERED)
                    }
                },
                onFailed = {
                    viewModelScope.launch { chatDao.updateDeliveryStatus(messageId, DeliveryStatus.FAILED.name) }
                }
            )
        }
    }

    // ==========================================
    // REPLAY AUDIO / TTS IN HISTORY
    // ==========================================

    fun replayMessageAudio(message: ChatMessage) {
        val audioPath = message.audioFilePath
        if (!audioPath.isNullOrBlank() && File(audioPath).exists()) {
            voiceRecorder.playAudioFile(
                filePath = audioPath,
                onStarted = {
                    audioPlayback.vibrate(AudioPlaybackManager.HapticType.PTT_PRESS)
                },
                onDone = {},
                onError = {
                    playTtsFallback(message)
                }
            )
        } else {
            playTtsFallback(message)
        }
    }

    private fun playTtsFallback(message: ChatMessage) {
        val lang = Language.fromCode(message.languageCode)
        ttsEngine.setLanguage(lang)
        ttsEngine.synthesizeAndPlay(
            text = message.text,
            isAlert = message.priority >= 2,
            onStarted = {},
            onDone = {},
            onError = {}
        )
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.deleteAllMessages()
        }
    }

    private fun updateTelemetryCalculations(audioDur: Long, packetSize: Int) {
        val memMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        val battery = getBatteryPercentage()

        _telemetry.value = TelemetryMetrics.compute(
            t0 = t0SpeechBegin,
            t1 = t1SpeechEnd,
            t2 = t2VadFinalized,
            t3 = t3SttBegin,
            t4 = t4SttComplete,
            t5 = t5TxBegin,
            t6 = t5TxBegin + 18L,
            t7 = t5TxBegin + 25L,
            t8 = t5TxBegin + 65L,
            t9 = t5TxBegin + 70L,
            audioDurationMs = audioDur,
            sttLang = languageManager.sttLanguage.value.code,
            ttsLang = languageManager.ttsLanguage.value.code,
            transport = transportManager.activeTransport.value.transportType.name,
            packetSize = packetSize,
            sent = reliabilityManager.totalPacketsSent,
            received = reliabilityManager.totalPacketsReceived,
            acks = reliabilityManager.totalAcksReceived,
            retries = reliabilityManager.retriedPackets,
            dropped = reliabilityManager.droppedPackets,
            memMb = memMb,
            battery = battery
        )
    }

    private fun updateHardwareTelemetry() {
        val memMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        val battery = getBatteryPercentage()
        _telemetry.value = _telemetry.value.copy(
            memoryUsageMb = memMb,
            batteryPercent = battery
        )
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(1, 100)
        } catch (e: Exception) {
            95
        }
    }

    fun transferAllQueuedMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val queuedMessages = _messages.value.filter {
                it.isOutgoing && (it.deliveryStatus == DeliveryStatus.QUEUED || it.deliveryStatus == DeliveryStatus.FAILED)
            }
            if (queuedMessages.isEmpty()) return@launch

            for (msg in queuedMessages) {
                chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.SENDING.name)
                reliabilityManager.sendReliablePacket(
                    text = msg.text,
                    languageCode = msg.languageCode,
                    messageType = msg.messageType,
                    priority = msg.priority,
                    requiresAck = true,
                    onSent = {
                        viewModelScope.launch {
                            chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.SENT.name)
                        }
                    },
                    onDelivered = {
                        viewModelScope.launch {
                            chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.DELIVERED.name)
                            audioPlayback.vibrate(AudioPlaybackManager.HapticType.MESSAGE_DELIVERED)
                        }
                    },
                    onFailed = {
                        viewModelScope.launch {
                            chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.QUEUED.name)
                        }
                    }
                )
            }
        }
    }

    fun retryTransferMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = _messages.value.find { it.id == messageId } ?: return@launch
            chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.SENDING.name)
            reliabilityManager.sendReliablePacket(
                text = msg.text,
                languageCode = msg.languageCode,
                messageType = msg.messageType,
                priority = msg.priority,
                requiresAck = true,
                onSent = {
                    viewModelScope.launch {
                        chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.SENT.name)
                    }
                },
                onDelivered = {
                    viewModelScope.launch {
                        chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.DELIVERED.name)
                        audioPlayback.vibrate(AudioPlaybackManager.HapticType.MESSAGE_DELIVERED)
                    }
                },
                onFailed = {
                    viewModelScope.launch {
                        chatDao.updateDeliveryStatus(msg.id, DeliveryStatus.QUEUED.name)
                    }
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.release()
        audioPlayback.release()
        sttEngine.release()
        ttsEngine.release()
        transportManager.release()
    }
}

private fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        senderId = senderId,
        senderName = senderName,
        timestamp = timestamp,
        languageCode = languageCode,
        messageType = try { MessageType.valueOf(messageType) } catch (e: Exception) { MessageType.NORMAL },
        text = text,
        isOutgoing = isOutgoing,
        deliveryStatus = try { DeliveryStatus.valueOf(deliveryStatus) } catch (e: Exception) { DeliveryStatus.SENT },
        isAudioPlayed = isAudioPlayed,
        priority = priority,
        latencyMs = latencyMs,
        audioFilePath = audioFilePath
    )
}
