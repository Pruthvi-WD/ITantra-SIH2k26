package com.example.domain.model

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val languageCode: String,
    val messageType: MessageType,
    val text: String,
    val isOutgoing: Boolean,
    val deliveryStatus: DeliveryStatus,
    val isAudioPlayed: Boolean = false,
    val priority: Int = 0,
    val latencyMs: Long = 0L,
    val audioFilePath: String? = null
)
