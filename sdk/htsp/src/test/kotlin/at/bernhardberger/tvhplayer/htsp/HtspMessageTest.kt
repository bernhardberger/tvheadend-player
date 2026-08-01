package at.bernhardberger.tvhplayer.htsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtspMessageTest {
    @Test
    fun typedAccessorsPreserveSupportedWireCoercions() {
        val payload = byteArrayOf(1, 2, 3)
        val nested = mapOf("value" to 9)
        val values = listOf("first", 2)
        val message = HtspMessage(
            method = "example",
            seq = 7,
            fields = mapOf(
                "int" to "42",
                "long" to 4_294_967_296L,
                "true" to "yes",
                "false" to 0,
                "string" to "value",
                "binary" to payload,
                "map" to nested,
                "list" to values,
            ),
        )

        assertEquals(42, message.int("int"))
        assertEquals(4_294_967_296L, message.long("long"))
        assertEquals(true, message.bool("true"))
        assertEquals(false, message.bool("false"))
        assertEquals("value", message.str("string"))
        assertArrayEquals(payload, message.bin("binary"))
        assertEquals(nested, message.map("map"))
        assertEquals(values, message.list("list"))
    }

    @Test
    fun equalityAndHashCodeUsePayloadContents() {
        val first = HtspMessage("muxpkt", 3, mapOf("stream" to 1), byteArrayOf(4, 5))
        val same = HtspMessage("muxpkt", 3, mapOf("stream" to 1), byteArrayOf(4, 5))
        val different = HtspMessage("muxpkt", 3, mapOf("stream" to 1), byteArrayOf(4, 6))

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertFalse(first == different)
        assertTrue(first != different)
    }
}
