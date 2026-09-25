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
    fun enhancedAc3IsNotLabelledAsPlainDolbyDigital() {
        assertEquals("Dolby Digital Plus", humanCodecName("audio/eac3"))
        assertEquals("Dolby Digital", humanCodecName("audio/ac3"))
    }

    @Test
    fun rowLinesSeparateNameLanguageAndFormat() {
        val german = humanLanguageName("de", "Unknown language")!!
        // ORF1 HD, September 2026: German main tracks, a "mul" second-channel track and
        // "Klare Sprache" as qaa with the hearing-impaired audio type.
        assertEquals(
            HumanTrackLabel("$german · Stereo", german, null, "Stereo · MPEG-1 Layer II"),
            label("de", 2, "audio/mpeg-L2", role = null),
        )
        assertEquals("5.1 · Dolby Digital", label("de", 6, "audio/ac3", role = null).detail)
        assertEquals(
            HumanTrackLabel("Unknown language · Stereo", "Unknown language", null, "Stereo · MPEG-1 Layer II"),
            label("mul", 2, "audio/mpeg-L2", role = null),
        )
        assertEquals(
            HumanTrackLabel("Clear dialogue · Stereo", "Clear dialogue", null, "Stereo · MPEG-1 Layer II"),
            label("qaa", 2, "audio/mpeg-L2", role = "Clear dialogue"),
        )
        assertEquals(
            HumanTrackLabel("$german · Stereo · Audio description", "Audio description", german, "Stereo · MPEG-1 Layer II"),
            label("de", 2, "audio/mpeg-L2", role = "Audio description"),
        )
        assertEquals(
            HumanTrackLabel("Audio description · Stereo", "Audio description", null, "Stereo · MPEG-1 Layer II"),
            label(null, 2, "audio/mpeg-L2", role = "Audio description"),
        )
        assertEquals(HumanTrackLabel("Stereo", "Stereo", null, "AAC"), label(null, 2, "audio/mp4a-latm", role = null))
    }

    @Test
    fun subtitleRowsNameTheirFormatInsteadOfTheMimeType() {
        assertEquals("Teletext", label("de", null, "application/x-media3-cues", role = null).detail)
        assertEquals("DVB", label("de", null, "application/dvbsubs", role = null).detail)
        assertNull(label("de", null, "application/x-unknown", role = null).detail)
    }

    private fun label(language: String?, channels: Int?, mime: String, role: String?) = humanTrackLabel(
        languageCode = language,
        channelCount = channels,
        sampleMimeType = mime,
        roleLabel = role,
        unknownLanguageLabel = "Unknown language",
        monoLabel = "Mono",
        stereoLabel = "Stereo",
        surround51Label = "5.1",
        surround71Label = "7.1",
        channelsLabel = { "$it channels" },
        trackFallbackLabel = "Track 1",
    )
}
