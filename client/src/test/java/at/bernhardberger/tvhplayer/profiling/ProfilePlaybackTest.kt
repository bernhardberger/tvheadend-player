@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.profiling

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePlaybackTest {
    // Generated from one synthetic 64x64 colour frame with libx264, with/without tff=1.
    private val frameOnly = hex("6764000aacd94426c044000003000400000300c83c489658")
    private val fieldCapable = hex("67640015acd9444d808800000300080000030190f8a14cb0")

    @Test
    fun standardParserDistinguishesCodingForRawAndAnnexBInitialization() {
        for (prefix in listOf(byteArrayOf(), byteArrayOf(0, 0, 1), byteArrayOf(0, 0, 0, 1))) {
            assertEquals("frameOnly", coding(prefix + frameOnly))
            assertEquals("fieldCapable", coding(prefix + fieldCapable))
        }
    }

    @Test
    fun missingMalformedNonSpsAndOversizedDataStayUnknown() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0, 0, 1, 0x67),
            byteArrayOf(0, 0, 1, 0x68), ByteArray(4097) { 0x67 })) {
            assertEquals("unknown", coding(bytes))
        }
    }

    private fun coding(bytes: ByteArray): String {
        val format = Format.Builder().setInitializationData(listOf(bytes)).build()
        // Keep this diagnostic helper private rather than adding a production testing API.
        val method = Class.forName("at.bernhardberger.tvhplayer.profiling.ProfilePlaybackKt")
            .getDeclaredMethod("avcCoding", Format::class.java).apply { isAccessible = true }
        return method.invoke(null, format) as String
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
