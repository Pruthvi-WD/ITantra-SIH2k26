package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val languageCode: String,
    val messageType: String,
    val text: String,
    val isOutgoing: Boolean,
    val deliveryStatus: String,
    val isAudioPlayed: Boolean,
    val priority: Int,
    val latencyMs: Long,
    val audioFilePath: String? = null
)
