package com.example.domain.model

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.zip.CRC32

/**
 * Lightweight communication packet for offline device-to-device transport.
 * Supports both JSON and compact binary wire formats.
 */
data class MessagePacket(
    val protocolVersion: Int = 1,
    val messageId: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val languageCode: String,
    val messageType: MessageType = MessageType.NORMAL,
    val sequence: Long = 0L,
    val text: String,
    val priority: Int = 0, // 0 = Normal, 1 = Important, 2 = Alert, 3 = Distress
    val requiresAck: Boolean = true,
    val ackMessageId: String? = null,
    val audioBase64: String? = null,
    val checksum: Long = 0L
) {
    /**
     * Compute CRC32 checksum for packet integrity.
     */
    fun withComputedChecksum(): MessagePacket {
        val crc = CRC32()
        crc.update(messageId.toByteArray())
        crc.update(senderId.toByteArray())
        crc.update(timestamp.toString().toByteArray())
        crc.update(text.toByteArray())
        if (audioBase64 != null) crc.update(audioBase64.toByteArray())
        return copy(checksum = crc.value)
    }

    /**
     * Verify CRC32 checksum.
     */
    fun isChecksumValid(): Boolean {
        if (checksum == 0L) return true // Legacy / unvalidated
        val crc = CRC32()
        crc.update(messageId.toByteArray())
        crc.update(senderId.toByteArray())
        crc.update(timestamp.toString().toByteArray())
        crc.update(text.toByteArray())
        if (audioBase64 != null) crc.update(audioBase64.toByteArray())
        return crc.value == checksum
    }

    /**
     * Serialize to JSON string for readable wire transport.
     */
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("v", protocolVersion)
        obj.put("id", messageId)
        obj.put("sid", senderId)
        obj.put("sname", senderName)
        obj.put("ts", timestamp)
        obj.put("lang", languageCode)
        obj.put("type", messageType.name)
        obj.put("seq", sequence)
        obj.put("txt", text)
        obj.put("prio", priority)
        obj.put("ack", requiresAck)
        if (ackMessageId != null) obj.put("ackId", ackMessageId)
        if (audioBase64 != null) obj.put("aud", audioBase64)
        obj.put("crc", checksum)
        return obj.toString()
    }

    /**
     * Binary serialization for ultra-low-bandwidth links.
     */
    fun toBinary(): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeByte(protocolVersion)
            out.writeUTF(messageId)
            out.writeUTF(senderId)
            out.writeUTF(senderName)
            out.writeLong(timestamp)
            out.writeUTF(languageCode)
            out.writeByte(messageType.ordinal)
            out.writeLong(sequence)
            out.writeUTF(text)
            out.writeByte(priority)
            out.writeBoolean(requiresAck)
            out.writeBoolean(ackMessageId != null)
            if (ackMessageId != null) out.writeUTF(ackMessageId)
            out.writeLong(checksum)
        }
        return baos.toByteArray()
    }

    companion object {
        fun fromJson(jsonStr: String): MessagePacket {
            val obj = JSONObject(jsonStr)
            return MessagePacket(
                protocolVersion = obj.optInt("v", 1),
                messageId = obj.getString("id"),
                senderId = obj.getString("sid"),
                senderName = obj.optString("sname", "Peer"),
                timestamp = obj.optLong("ts", System.currentTimeMillis()),
                languageCode = obj.optString("lang", "en-IN"),
                messageType = try {
                    MessageType.valueOf(obj.optString("type", MessageType.NORMAL.name))
                } catch (e: Exception) {
                    MessageType.NORMAL
                },
                sequence = obj.optLong("seq", 0L),
                text = obj.optString("txt", ""),
                priority = obj.optInt("prio", 0),
                requiresAck = obj.optBoolean("ack", true),
                ackMessageId = if (obj.has("ackId")) obj.getString("ackId") else null,
                audioBase64 = if (obj.has("aud")) obj.getString("aud") else null,
                checksum = obj.optLong("crc", 0L)
            )
        }

        fun fromBinary(data: ByteArray): MessagePacket {
            val bais = ByteArrayInputStream(data)
            DataInputStream(bais).use { input ->
                val v = input.readByte().toInt()
                val id = input.readUTF()
                val sid = input.readUTF()
                val sname = input.readUTF()
                val ts = input.readLong()
                val lang = input.readUTF()
                val typeOrdinal = input.readByte().toInt()
                val type = MessageType.entries.getOrElse(typeOrdinal) { MessageType.NORMAL }
                val seq = input.readLong()
                val txt = input.readUTF()
                val prio = input.readByte().toInt()
                val ack = input.readBoolean()
                val hasAckId = input.readBoolean()
                val ackId = if (hasAckId) input.readUTF() else null
                val crc = input.readLong()

                return MessagePacket(
                    protocolVersion = v,
                    messageId = id,
                    senderId = sid,
                    senderName = sname,
                    timestamp = ts,
                    languageCode = lang,
                    messageType = type,
                    sequence = seq,
                    text = txt,
                    priority = prio,
                    requiresAck = ack,
                    ackMessageId = ackId,
                    checksum = crc
                )
            }
        }

        fun createAck(original: MessagePacket, mySenderId: String, mySenderName: String): MessagePacket {
            return MessagePacket(
                protocolVersion = original.protocolVersion,
                senderId = mySenderId,
                senderName = mySenderName,
                sequence = original.sequence,
                languageCode = original.languageCode,
                messageType = MessageType.ACK,
                text = "ACK",
                priority = 0,
                requiresAck = false,
                ackMessageId = original.messageId
            ).withComputedChecksum()
        }
    }
}
