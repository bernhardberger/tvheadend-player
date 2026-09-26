package at.bernhardberger.tvhplayer.core

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PlayerChromePolicyTest {
    @Test fun liveEdgeUsesExistingTolerance() {
        assertEquals(PlayerStatusKind.LIVE, playerStatus(behindLiveSeconds = 3)!!.kind)
        assertEquals(PlayerStatusKind.LIVE, playerStatus(behindLiveSeconds = TIMESHIFT_LIVE_EDGE_TOLERANCE_MS / 1000)!!.kind)
        assertEquals(PlayerStatusKind.BEHIND_LIVE, playerStatus(behindLiveSeconds = TIMESHIFT_LIVE_EDGE_TOLERANCE_MS / 1000 + 1)!!.kind)
    }

    @Test fun unknownTimingAndPausedZeroDoNotInventBehindLive() {
        assertNull(playerStatus(timingKnown = false))
        assertNull(playerStatus(timingKnown = false, paused = true, behindLiveSeconds = 20))
        assertEquals(PlayerStatus(PlayerStatusKind.PAUSED, PlayerStatusIndicator.PAUSE), playerStatus(paused = true, behindLiveSeconds = 0))
        assertEquals(PlayerStatusKind.BUFFERING, playerStatus(timingKnown = false, buffering = true)!!.kind)
    }

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
    @Test fun liveIsNeutralWithoutDot() {
        assertEquals(PlayerStatus(PlayerStatusKind.LIVE, PlayerStatusIndicator.NONE), playerStatus())
        assertEquals(playerStatus(), playerStatus(behindLiveSeconds = -10))
    }

    @Test fun everyPlaybackStatusAndIndependentRecordingMarker() {
        assertEquals(PlayerStatus(PlayerStatusKind.BEHIND_LIVE, PlayerStatusIndicator.PLAY, 203), playerStatus(behindLiveSeconds = 203))
        assertEquals(PlayerStatus(PlayerStatusKind.PAUSED, PlayerStatusIndicator.PAUSE, 203), playerStatus(paused = true, behindLiveSeconds = 203))
        assertEquals(PlayerStatusKind.TUNING, playerStatus(tuning = true)!!.kind)
        assertEquals(PlayerStatusKind.BUFFERING, playerStatus(buffering = true)!!.kind)
        assertEquals(PlayerStatusIndicator.SPINNER, playerStatus(tuning = true)!!.indicator)
        assertEquals(PlayerStatusIndicator.SPINNER, playerStatus(buffering = true)!!.indicator)
        assertEquals(PlayerStatusKind.GROWING_RECORDING, playerStatus(live = false, growingRecording = true)!!.kind)
        assertEquals(PlayerStatusIndicator.RECORDING_DOT, playerStatus(live = false, growingRecording = true)!!.indicator)
        assertNull(playerStatus(live = false))
        assertEquals(PlayerStatusKind.PAUSED, playerStatus(live = false, paused = true)!!.kind)
        assertNull(playerStatus(live = false, paused = true, behindLiveSeconds = 12)!!.behindLiveSeconds)
        assertEquals(PlayerStatus(PlayerStatusKind.PROBLEM, PlayerStatusIndicator.ERROR, problem = "Unavailable"), playerStatus(problem = "Unavailable"))
        assertNull(recordingNowStatus(false))
        assertEquals(PlayerStatus(PlayerStatusKind.RECORDING, PlayerStatusIndicator.RECORDING_DOT), recordingNowStatus(true))
    }

    @Test fun transientAndFailurePrecedence() {
        assertEquals(PlayerStatusKind.PROBLEM, playerStatus(tuning = true, buffering = true, paused = true, problem = "Unavailable")!!.kind)
        assertEquals(PlayerStatusKind.TUNING, playerStatus(tuning = true, buffering = true, paused = true)!!.kind)
        assertEquals(PlayerStatusKind.BUFFERING, playerStatus(buffering = true, paused = true)!!.kind)
        assertEquals(PlayerStatusKind.PAUSED, playerStatus(paused = true, growingRecording = true)!!.kind)
        assertEquals(playerStatus(), playerStatus(problem = " "))
    }

    @Test fun badgesAreOrderedCappedAndReuseAudioNamesWithoutInventingScanOrHdr() {
        val badges = glanceBadges(1080, "video/avc", "audio/eac3", 6, true, true, true, true, 82.0, 12.3, locale = Locale.ENGLISH)
        assertEquals(listOf(GlanceBadgeKind.RASTER, GlanceBadgeKind.VIDEO, GlanceBadgeKind.AUDIO,
            GlanceBadgeKind.AD, GlanceBadgeKind.SUB, GlanceBadgeKind.TXT, GlanceBadgeKind.SNR_PERCENT), badges.map { it.kind })
        assertEquals("1080", badges.first().value)
        assertEquals("${shortCodecName("audio/eac3")} 5.1", badges[2].value)
        assertEquals("82", badges.last().value)
        assertEquals(badges.take(2), glanceBadges(1080, "video/avc", "audio/eac3", maxBadges = 2))
        assertTrue(glanceBadges(1080, maxBadges = -1).isEmpty())
    }

    @Test fun snrRequiresLiveFrontendAndKnownFiniteValues() {
        assertTrue(glanceBadges().isEmpty())
        assertTrue(glanceBadges(videoHeight = 0, videoSampleMimeType = " ").isEmpty())
        assertTrue(glanceBadges(relativeSnrPercent = 82.0, absoluteSnrDecibels = 12.3).isEmpty())
        assertTrue(glanceBadges(liveFrontend = true).isEmpty())
        assertTrue(glanceBadges(liveFrontend = true, relativeSnrPercent = Double.NaN, absoluteSnrDecibels = Double.POSITIVE_INFINITY).isEmpty())
        assertEquals(listOf(GlanceBadge(GlanceBadgeKind.SNR_DB, "12.3")), glanceBadges(liveFrontend = true, absoluteSnrDecibels = 12.3, locale = Locale.ENGLISH))
        assertEquals("12,3", glanceBadges(liveFrontend = true, absoluteSnrDecibels = 12.3, locale = Locale.GERMAN).single().value)
        assertEquals("0", glanceBadges(liveFrontend = true, relativeSnrPercent = 0.0).single().value)
        assertEquals(GlanceBadgeKind.SNR_DB, glanceBadges(liveFrontend = true, relativeSnrPercent = 101.0, absoluteSnrDecibels = 12.3).single().kind)
    }

    @Test fun layoutRetainsOnlyPriorityPrefixInTwoRows() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), glanceBadgeRows(listOf(40, 40, 40, 40, 40), 84, 4))
        assertEquals(listOf(listOf(0), listOf(1)), glanceBadgeRows(listOf(52, 52, 52), 84, 4))
        assertTrue(glanceBadgeRows(listOf(100, 20), 84, 4).isEmpty())
        assertTrue(glanceBadgeRows(emptyList(), 84, 4).isEmpty())
    }

    @Test fun detailsOmitEmptyRowsSectionsAndNonDvbReception() {
        val reception = listOf(StreamSignalRow("SNR", "82 %"))
        assertTrue(streamSignalDetails(reception = reception).isEmpty())
        assertTrue(streamSignalDetails(liveFrontend = true).isEmpty())
        assertEquals(listOf(StreamSignalSection.STREAM, StreamSignalSection.RECEPTION), streamSignalDetails(
            stream = listOf(StreamSignalRow("Video", "H.264"), StreamSignalRow("Unknown", null)),
            source = listOf(StreamSignalRow("Source", " ")), reception = reception, liveFrontend = true,
        ).keys.toList())
    }
}
