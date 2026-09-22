package com.example

import com.example.domain.model.Language
import com.example.domain.model.MessagePacket
import com.example.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun testAllTenIndianLanguagesConfigured() {
        val languages = Language.entries
        assertEquals(10, languages.size)
        assertTrue(languages.any { it.code.startsWith("hi") })
        assertTrue(languages.any { it.code.startsWith("gu") })
        assertTrue(languages.any { it.code.startsWith("mr") })
        assertTrue(languages.any { it.code.startsWith("kn") })
        assertTrue(languages.any { it.code.startsWith("ml") })
        assertTrue(languages.any { it.code.startsWith("ta") })
        assertTrue(languages.any { it.code.startsWith("te") })
        assertTrue(languages.any { it.code.startsWith("or") })
        assertTrue(languages.any { it.code.startsWith("bn") })
        assertTrue(languages.any { it.code.startsWith("en") })
    }

    @Test
    fun testMessagePacketSerializationAndChecksum() {
        val packet = MessagePacket(
            senderId = "dev01",
            senderName = "AlphaUnit",
            languageCode = "hi-IN",
            messageType = MessageType.NORMAL,
            sequence = 1L,
            text = "नमस्ते, हम सुरक्षित हैं।"
        ).withComputedChecksum()

        assertTrue(packet.isChecksumValid())

        val json = packet.toJson()
        val deserialized = MessagePacket.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(packet.messageId, deserialized?.messageId)
        assertEquals(packet.text, deserialized?.text)
        assertTrue(deserialized?.isChecksumValid() == true)
    }

    @Test
    fun testAckPacketCreation() {
        val original = MessagePacket(
            senderId = "devA",
            senderName = "PeerA",
            languageCode = "kn-IN",
            messageType = MessageType.ALERT,
            sequence = 42L,
            text = "ತುರ್ತು ಪರಿಸ್ಥಿತಿ"
        ).withComputedChecksum()

        val ack = MessagePacket.createAck(
            original = original,
            mySenderId = "devB",
            mySenderName = "PeerB"
        )

        assertEquals(MessageType.ACK, ack.messageType)
        assertEquals(original.messageId, ack.ackMessageId)
        assertEquals(42L, ack.sequence)
        assertTrue(ack.isChecksumValid())
    }
}
