package at.bernhardberger.tvhplayer.core

import org.junit.Assert.*
import org.junit.Test

class PlayerChromePolicyTest {
    @Test fun badgesUseDeclaredScanAndCompactAudioNamesInOrder() {
        listOf(1080 to VideoScan.INTERLACED, 720 to VideoScan.PROGRESSIVE,
            576 to VideoScan.INTERLACED, 2160 to VideoScan.PROGRESSIVE, 1080 to null)
            .zip(listOf("1080i", "720p", "576i", "2160p", "1080")).forEach { (input, label) ->
                assertEquals(label, glanceBadges(input.first, scan = input.second).single().value)
            }
        val badges = glanceBadges(2160, "audio/eac3", 6, true, VideoScan.PROGRESSIVE)
        assertEquals(listOf(GlanceBadgeKind.RASTER, GlanceBadgeKind.AUDIO, GlanceBadgeKind.AD), badges.map { it.kind })
        assertEquals("DD+ 5.1", badges[1].value)
        assertEquals("Dolby Digital Plus", humanCodecName("audio/eac3"))
        assertTrue(glanceBadges().isEmpty())
        assertTrue(glanceBadges(videoHeight = -1, audioMimeType = "unknown").isEmpty())
    }

    @Test fun audioLayoutsAreOnlyNamedForKnownChannelCounts() {
        mapOf("audio/ac3" to "DD", "audio/mpeg-L2" to "MP2", "audio/mp4a-latm" to "AAC",
            "audio/eac3" to "DD+", "audio/ac4" to "AC-4").forEach { (mime, codec) ->
            mapOf(1 to "1.0", 2 to "2.0", 6 to "5.1", 8 to "7.1", 0 to null, -1 to null, 4 to null)
                .forEach { (count, layout) ->
                    assertEquals(listOfNotNull(codec, layout).joinToString(" "),
                        glanceBadges(audioMimeType = mime, audioChannelCount = count).single().value)
                }
        }
    }
}
