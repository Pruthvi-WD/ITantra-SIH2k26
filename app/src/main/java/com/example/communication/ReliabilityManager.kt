package com.example.communication

import com.example.domain.model.MessagePacket
import com.example.domain.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Reliability and packet ordering layer.
 * Implements ACK tracking, automatic retries with exponential backoff,
 * duplicate packet filtering, and sequence numbering.
 */
class ReliabilityManager(
    private val transportManager: TransportManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sequenceCounter = AtomicLong(1L)

    // Deduplication cache: keeps last 200 message IDs
    private val seenMessageIds = java.util.Collections.newSetFromMap(
        object : java.util.LinkedHashMap<String, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 200
            }
        }
    )

    // Pending ACK tracking
    data class PendingPacket(
        val packet: MessagePacket,
        var retryCount: Int = 0,
        val maxRetries: Int = 3,
        val onDelivered: () -> Unit,
        val onFailed: () -> Unit,
        var timeoutJob: Job? = null
    )

    private val pendingAcks = ConcurrentHashMap<String, PendingPacket>()

    // Statistics for Developer Dashboard
    var totalPacketsSent = 0L
        private set
    var totalPacketsReceived = 0L
        private set
    var totalAcksReceived = 0L
        private set
    var retriedPackets = 0L
        private set
    var droppedPackets = 0L
        private set

    private val _validIncomingPackets = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    val validIncomingPackets: SharedFlow<MessagePacket> = _validIncomingPackets.asSharedFlow()

    init {
        scope.launch {
            transportManager.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    suspend fun sendReliablePacket(
        text: String,
        languageCode: String,
        messageType: MessageType,
        priority: Int,
        requiresAck: Boolean = true,
        audioBase64: String? = null,
        onSent: () -> Unit = {},
        onDelivered: () -> Unit = {},
        onFailed: () -> Unit = {}
    ): MessagePacket {
        val seq = sequenceCounter.getAndIncrement()
        val packet = MessagePacket(
            senderId = transportManager.localDeviceId,
            senderName = transportManager.localDeviceName,
            languageCode = languageCode,
            messageType = messageType,
            sequence = seq,
            text = text,
            priority = priority,
            requiresAck = requiresAck,
            audioBase64 = audioBase64
        ).withComputedChecksum()

        totalPacketsSent++
        val sentImmediately = transportManager.sendPacket(packet)

        if (sentImmediately) {
            onSent()
            if (requiresAck) {
                trackAck(packet, onDelivered, onFailed)
            } else {
                onDelivered()
            }
        } else {
            droppedPackets++
            onFailed()
        }

        return packet
    }

    private fun trackAck(packet: MessagePacket, onDelivered: () -> Unit, onFailed: () -> Unit) {
        val pending = PendingPacket(
            packet = packet,
            onDelivered = onDelivered,
            onFailed = onFailed
        )

        pending.timeoutJob = scope.launch {
            var attempt = 0
            var delayMs = 900L

            while (isActive && attempt < pending.maxRetries) {
                delay(delayMs)
                if (!pendingAcks.containsKey(packet.messageId)) {
                    // ACK already received
                    return@launch
                }

                // Attempt retry
                attempt++
                pending.retryCount = attempt
                retriedPackets++
                val reSent = transportManager.sendPacket(packet)
                if (!reSent) {
                    delayMs = (delayMs * 1.5).toLong()
                }
            }

            // Exceeded retries
            if (pendingAcks.remove(packet.messageId) != null) {
                droppedPackets++
                onFailed()
            }
        }

        pendingAcks[packet.messageId] = pending
    }

    private suspend fun handleIncomingPacket(packet: MessagePacket) {
        // 1. If ACK packet, resolve pending
        if (packet.messageType == MessageType.ACK) {
            packet.ackMessageId?.let { ackId ->
                val pending = pendingAcks.remove(ackId)
                pending?.timeoutJob?.cancel()
                pending?.onDelivered?.invoke()
                totalAcksReceived++
            }
            return
        }

        // 2. Deduplication check
        synchronized(seenMessageIds) {
            if (seenMessageIds.contains(packet.messageId)) {
                // Duplicate packet received; resend ACK if required
                if (packet.requiresAck) {
                    sendAck(packet)
                }
                return
            }
            seenMessageIds.add(packet.messageId)
        }

        totalPacketsReceived++

        // 3. Send ACK immediately back to sender
        if (packet.requiresAck) {
            sendAck(packet)
        }

        // 4. Dispatch valid packet to UI / TTS
        _validIncomingPackets.emit(packet)
    }

    private fun sendAck(original: MessagePacket) {
        scope.launch {
            val ack = MessagePacket.createAck(
                original = original,
                mySenderId = transportManager.localDeviceId,
                mySenderName = transportManager.localDeviceName
            )
            transportManager.sendPacket(ack)
        }
    }
}
