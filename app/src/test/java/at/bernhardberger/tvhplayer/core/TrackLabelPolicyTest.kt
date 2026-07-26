package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLabelPolicyTest {
    @Test
    fun languageCodesMapToDisplayNamesAndSpecialFallbacks() {
        assertEquals("Unknown language", humanLanguageName("und", "Unknown language"))
        assertEquals("Unknown language", humanLanguageName("mis", "Unknown language"))
        assertEquals("Unknown language", humanLanguageName("zxx", "Unknown language"))
        val german = humanLanguageName("de", "Unknown language")
        assertTrue(german == "German" || german == "Deutsch" || german == "de")
    }

    @Test
    fun channelLayoutsUseFriendlyNames() {
        assertEquals(
            "Stereo",
            humanChannelLayout(2, "Mono", "Stereo", "5.1", "7.1") { "$it channels" },
        )
        assertEquals(
            "5.1",
            humanChannelLayout(6, "Mono", "Stereo", "5.1", "7.1") { "$it channels" },
        )
        assertEquals(
            "4 channels",
            humanChannelLayout(4, "Mono", "Stereo", "5.1", "7.1") { "$it channels" },
        )
    }

    @Test
    fun primaryLabelPrefersLanguageAndLayoutOverCodec() {
        val label = humanTrackLabel(
            languageCode = "de",
            channelCount = 6,
            sampleRateHz = 48_000,
            sampleMimeType = "audio/ac3",
            roleLabel = null,
            unknownLanguageLabel = "Unknown language",
            monoLabel = "Mono",
            stereoLabel = "Stereo",
            surround51Label = "5.1",
            surround71Label = "7.1",
            channelsLabel = { "$it channels" },
            trackFallbackLabel = "Track 1",
        )
        assertTrue(label.primary.contains("5.1"))
        assertTrue(label.secondary?.contains("48000 Hz") == true)
        assertTrue(label.secondary?.contains("Dolby Digital") == true)
        assertNull(label.secondary?.takeIf { it.contains("audio/ac3") })
    }
}
