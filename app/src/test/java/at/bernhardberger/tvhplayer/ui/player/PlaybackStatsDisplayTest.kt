package at.bernhardberger.tvhplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStatsDisplayTest {
    @Test
    fun refreshRatesPrintBareWhenWholeAndTwoDecimalsWhenFractional() {
        assertEquals("60", formatRefreshRate(60f))
        assertEquals("50", formatRefreshRate(50.0001f))
        assertEquals("59.94", formatRefreshRate(59.940002f))
        assertEquals("23.98", formatRefreshRate(23.976f))
        assertEquals("29.97", formatRefreshRate(29.97f))
        assertEquals("90.5", formatRefreshRate(90.5f))
    }

    @Test
    fun refreshRatesListEveryResolutionLargestFirstDeduplicatedAndSorted() {
        val modes = listOf(
            DisplayModeSpec(3840, 2160, 60f),
            DisplayModeSpec(1920, 1080, 50f),
            DisplayModeSpec(3840, 2160, 30f),
            DisplayModeSpec(1920, 1080, 23.976f),
            DisplayModeSpec(1920, 1080, 60f),
            DisplayModeSpec(3840, 2160, 60.0001f),
            DisplayModeSpec(1280, 720, 50f),
        )

        assertEquals(
            "2160p: 30 · 60 / 1080p: 23.98 · 50 · 60 / 720p: 50",
            refreshRatesByResolution(modes),
        )
        assertEquals("", refreshRatesByResolution(emptyList()))
    }

    @Test
    fun resolutionsSharingAHeightKeepTheirFullSize() {
        val modes = listOf(DisplayModeSpec(3840, 2160, 60f), DisplayModeSpec(4096, 2160, 24f))

        assertEquals("4096×2160: 24 / 3840×2160: 60", refreshRatesByResolution(modes))
    }

    @Test
    fun sameTimeFramesShowAsAPercentOnlyWhenPresent() {
        assertNull(sameTimeFramePercent(0f))
        assertEquals(48, sameTimeFramePercent(0.48f))
        assertEquals(1, sameTimeFramePercent(0.001f))
        assertEquals(100, sameTimeFramePercent(1f))
    }

    @Test
    fun matchPreferenceMapsKnownValuesOnly() {
        assertEquals(MatchFrameRatePreference.NEVER, matchFrameRatePreference(0))
        assertEquals(MatchFrameRatePreference.SEAMLESS_ONLY, matchFrameRatePreference(1))
        assertEquals(MatchFrameRatePreference.ALWAYS, matchFrameRatePreference(2))
        assertNull(matchFrameRatePreference(-1))
    }
}
