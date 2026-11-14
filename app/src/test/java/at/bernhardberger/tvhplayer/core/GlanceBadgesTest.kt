package at.bernhardberger.tvhplayer.core

import org.junit.Assert.*
import org.junit.Test

class GlanceBadgesTest {
    @Test fun badgesUseDeclaredScanAndCompactAudioNamesInOrder() {
        listOf(1080 to VideoScan.INTERLACED, 720 to VideoScan.PROGRESSIVE,
            576 to VideoScan.INTERLACED, 2160 to VideoScan.PROGRESSIVE, 1080 to null)
            .zip(listOf("1080i", "720p", "576i", "2160p", "1080")).forEach { (input, label) ->
                assertEquals(label, glanceBadges(input.first, scan = input.second).single().value)
            }
        val badges = glanceBadges(2160, "audio/eac3", 6, true, VideoScan.PROGRESSIVE)
        assertEquals(listOf(GlanceBadgeKind.RASTER, GlanceBadgeKind.AUDIO, GlanceBadgeKind.AD), badges.map { it.kind })
        assertEquals("DD+ 5.1", badges[1].value)
        assertTrue(glanceBadges().isEmpty())
        assertTrue(glanceBadges(videoHeight = -1, audioMimeType = "unknown").isEmpty())
    }

    @Test fun audioLayoutsAreOnlyNamedForKnownChannelCounts() {
        mapOf(1 to "1.0", 2 to "2.0", 6 to "5.1", 8 to "7.1", 4 to null).forEach { (count, layout) ->
            assertEquals(listOfNotNull("DD", layout).joinToString(" "),
                glanceBadges(audioMimeType = "audio/ac3", audioChannelCount = count).single().value)
        }
    }
}
