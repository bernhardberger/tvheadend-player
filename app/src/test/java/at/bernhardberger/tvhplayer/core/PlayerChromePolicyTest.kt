package at.bernhardberger.tvhplayer.core

import org.junit.Assert.*
import org.junit.Test

class PlayerChromePolicyTest {
    @Test fun shortCodecNamesAndExplicitScanAreSharedWithoutChangingFullNames() {
        mapOf("audio/eac3" to "DD+", "audio/ac3" to "DD", "audio/ac4" to "AC-4",
            "audio/mp4a-latm" to "AAC", "audio/mpeg-L2" to "MP2", "audio/opus" to "Opus").forEach { (mime, name) ->
            assertEquals(name, shortCodecName(mime))
        }
        assertEquals("Dolby Digital Plus", humanCodecName("audio/eac3"))
        assertEquals("1080i", glanceBadges(1080, scan = VideoScan.INTERLACED).single().value)
        assertEquals("1080p", glanceBadges(1080, scan = VideoScan.PROGRESSIVE).single().value)
        assertEquals("1080", glanceBadges(1080).single().value)
    }

    @Test fun sampleMimeTypesProduceReadableVideoBadges() {
        assertEquals("H.264", glanceBadges(1080, "video/avc").last().value)
        assertEquals("HEVC", glanceBadges(2160, "video/hevc").last().value)
        assertEquals("MPEG-2", glanceBadges(576, "video/mpeg2").last().value)
        assertFalse(glanceBadges(1080, "avc1.640028").any { it.value == "avc1.640028" })
    }
    @Test fun badgesAreOrderedAndReuseAudioNamesWithoutInventingScanOrHdr() {
        val badges = glanceBadges(1080, "video/avc", "audio/eac3", 6, true, true, true)
        assertEquals(listOf(GlanceBadgeKind.RASTER, GlanceBadgeKind.VIDEO, GlanceBadgeKind.AUDIO,
            GlanceBadgeKind.AD, GlanceBadgeKind.SUB, GlanceBadgeKind.TXT), badges.map { it.kind })
        assertEquals("1080", badges.first().value)
        assertEquals("${shortCodecName("audio/eac3")} 5.1", badges[2].value)
        assertTrue(glanceBadges().isEmpty())
        assertTrue(glanceBadges(videoHeight = 0, videoSampleMimeType = " ").isEmpty())
    }

}
