package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDisplayModeTest {
    private val current = DisplayModeSpec(1920, 1080, 60f, 1)

    @Test
    fun displayModeRequestsStayInBrowseEvenAtTheBaseRate() {
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.DISPLAY_MODE, 60f, current))
    }

    @Test
    fun android11SeamlessRequestsDropInBrowse() {
        assertFalse(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.SEAMLESS_DEFAULT, 24f, null))
    }

    @Test
    fun alwaysDropsWhenTheBaseOrItsAlternativeMatches() {
        assertFalse(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 60f, current))
        val seamless = current.copy(alternativeRefreshRates = listOf(24f, 50f))
        assertFalse(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 24.0001f, seamless))
        assertFalse(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 50f, seamless))
    }

    @Test
    fun alwaysKeepsNonSeamlessAndUnknownBaseRequests() {
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 24f, current))
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 24f, null))
        // Matching multiples do not count, and NTSC is distinct from whole cadence.
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 30f, current))
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 59.94f, current))
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 23.976f,
            current.copy(alternativeRefreshRates = listOf(24f))))
    }

    @Test
    fun browseRetentionComparesTheBaseRatherThanTheMatchingCurrentMode() {
        val switched = current.copy(refreshRate = 24f, modeId = 2)
        assertEquals(switched, videoDisplayMode(24f, switched, listOf(current, switched)))
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 24f, current))
    }

    @Test
    fun baseSurvivesClearStaleObservationRenewedRequestAndBrowse() {
        var base = nextBaseMode(null, current, unrequested = true, lastRequestedRate = null)
        val switched = current.copy(refreshRate = 24f, modeId = 2)
        base = nextBaseMode(base, switched, unrequested = false, lastRequestedRate = 24f)
        // Radio or a settings toggle clears the request, but the display has not returned yet.
        base = nextBaseMode(base, switched, unrequested = true, lastRequestedRate = 24f)
        assertEquals(current, base)
        // A renewed 24 Hz request and then browse must still compare against the original 60 Hz.
        base = nextBaseMode(base, switched, unrequested = false, lastRequestedRate = 24f)
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 24f, base))
        assertEquals(current, nextBaseMode(base, current.copy(refreshRate = 50f), true, 24f))
    }

    @Test
    fun staleLastRequestedRateIsRejectedAndUnknownBaseKeepsTheRequest() {
        val stale = current.copy(refreshRate = 24.0001f, modeId = 2)
        val base = nextBaseMode(null, stale, unrequested = true, lastRequestedRate = 24f)
        assertNull(base)
        assertTrue(keepVideoFrameRateInBrowse(SurfaceFrameRateChange.ALWAYS, 24f, base))
        assertEquals(current, nextBaseMode(base, current, unrequested = true, lastRequestedRate = 24f))
    }

    @Test
    fun firstCleanObservationIsAcceptedOnlyWithoutARequest() {
        assertNull(nextBaseMode(null, current, unrequested = false, lastRequestedRate = null))
        assertNull(nextBaseMode(null, null, unrequested = true, lastRequestedRate = null))
        assertEquals(current, nextBaseMode(null, current, unrequested = true, lastRequestedRate = null))
        // Whole and NTSC rates remain distinct in the observation guard, too.
        assertEquals(current, nextBaseMode(null, current, unrequested = true, lastRequestedRate = 59.94f))
    }

    @Test
    fun absentOrInvalidVideoNeverKeepsARequest() {
        for (change in SurfaceFrameRateChange.entries) {
            for (rate in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
                assertFalse(keepVideoFrameRateInBrowse(change, rate, null))
            }
        }
    }

    @Test
    fun selectsTheRequestedCadenceAtTheCurrentResolution() {
        val fifty = current.copy(refreshRate = 50f, modeId = 2)
        assertEquals(fifty, videoDisplayMode(50f, current, listOf(current, fifty)))
        assertEquals(fifty, videoDisplayMode(50.0001f, current, listOf(fifty)))
    }

    @Test
    fun fractionalAndWholeRatesAreDistinctInBothDirections() {
        for ((fractional, whole) in listOf(59.94f to 60f, 23.976f to 24f)) {
            val active = current.copy(refreshRate = 50f)
            val fractionalMode = current.copy(refreshRate = fractional, modeId = 2)
            val wholeMode = current.copy(refreshRate = whole, modeId = 3)
            assertEquals(fractionalMode, videoDisplayMode(fractional, active, listOf(wholeMode, fractionalMode)))
            assertEquals(wholeMode, videoDisplayMode(whole, active, listOf(fractionalMode, wholeMode)))
            assertNull(videoDisplayMode(fractional, wholeMode, listOf(wholeMode)))
            assertNull(videoDisplayMode(whole, fractionalMode, listOf(fractionalMode)))
        }
    }

    @Test
    fun ignoresMatchesWithADifferentWidthOrHeight() {
        assertNull(
            videoDisplayMode(
                50f, current,
                listOf(current.copy(width = 3840, refreshRate = 50f), current.copy(height = 720, refreshRate = 50f)),
            ),
        )
    }

    @Test
    fun keepsTheCurrentMatchingModeEvenWhenAnotherMatchingModeComesFirst() {
        assertEquals(current, videoDisplayMode(60f, current, listOf(current.copy(modeId = 2), current)))
    }

    @Test
    fun noMatchDoesNotPickAMultipleOrAnotherRate() {
        assertNull(videoDisplayMode(30f, current, listOf(current)))
        assertNull(videoDisplayMode(50f, current, emptyList()))
        assertNull(videoDisplayMode(Float.NaN, current, listOf(current)))
        assertNull(videoDisplayMode(0f, current, listOf(current)))
    }

    @Test
    fun reselectingTheCurrentModePinsItInsteadOfRestoringTheDefault() {
        val fifty = current.copy(refreshRate = 50f, modeId = 2)
        val pendingFilm = current.copy(refreshRate = 24f, modeId = 3)
        assertEquals(fifty.modeId, displayModeIdToRequest(fifty, fifty, pendingFilm.modeId))
    }

    @Test
    fun anAppBaselinePinsTheCurrentModeWithoutAnExistingPreference() {
        val fifty = current.copy(refreshRate = 50f, modeId = 2)
        assertEquals(fifty.modeId, displayModeIdToRequest(fifty, fifty, 0, pin = true))
    }

    @Test
    fun selectingAnotherModeRequestsItsId() {
        val fifty = current.copy(refreshRate = 50f, modeId = 2)
        assertEquals(fifty.modeId, displayModeIdToRequest(fifty, current, 0))
        assertEquals(fifty.modeId, displayModeIdToRequest(fifty, current, current.modeId))
    }

    @Test
    fun anAlreadyPreferredModeOrAnUnpreferredMatchingCurrentModeNeedsNoUpdate() {
        val fifty = current.copy(refreshRate = 50f, modeId = 2)
        assertNull(displayModeIdToRequest(fifty, current, fifty.modeId))
        assertNull(displayModeIdToRequest(current, current, current.modeId))
        assertNull(displayModeIdToRequest(current, current, 0))
    }

    @Test
    fun noMatchingModeClearsAnExistingPreferenceOnly() {
        assertEquals(0, displayModeIdToRequest(null, current, current.modeId))
        assertNull(displayModeIdToRequest(null, current, 0))
    }

    @Test
    fun refreshRateChoicesAreTheCurrentResolutionsDistinctRates() {
        val supported = listOf(
            current, current.copy(refreshRate = 59.94f, modeId = 2), current.copy(refreshRate = 50f, modeId = 3),
            current.copy(refreshRate = 50.0001f, modeId = 4), current.copy(width = 3840, height = 2160, refreshRate = 24f, modeId = 5),
        )
        assertEquals(listOf(50f, 59.94f, 60f), displayRefreshRateChoices(current, supported))
        assertEquals(listOf(60f), displayRefreshRateChoices(current, emptyList()))
    }

    @Test
    fun theWindowPrefersTheVideosModeAndFallsBackToTheAppRate() {
        val fifty = current.copy(refreshRate = 50f, modeId = 2)
        val film = current.copy(refreshRate = 24f, modeId = 3)
        val modes = listOf(current, fifty, film)
        fun select(change: SurfaceFrameRateChange?, rate: Float?, appRate: Float?, allowSwitch: Boolean = false) =
            windowDisplayMode(change, rate, appRate, current, modes, systemAllowsAnySwitch = allowSwitch)

        assertNull(select(SurfaceFrameRateChange.ALWAYS, 24f, 50f, allowSwitch = true))
        assertEquals(fifty, select(SurfaceFrameRateChange.ALWAYS, 24f, 50f))
        assertNull(select(SurfaceFrameRateChange.ALWAYS, 24f, null))
        assertNull(select(SurfaceFrameRateChange.ALWAYS, 24f, null, allowSwitch = true))
        assertEquals(fifty, select(SurfaceFrameRateChange.ALWAYS, null, 50f, allowSwitch = true))
        assertEquals(fifty, select(null, null, 50f))
        assertEquals(fifty, select(SurfaceFrameRateChange.SEAMLESS_DEFAULT, 24f, 50f))
        assertEquals(film, select(SurfaceFrameRateChange.DISPLAY_MODE, 24f, null))
        assertEquals(film, select(SurfaceFrameRateChange.DISPLAY_MODE, 24f, 50f))
        // A video rate the display lacks, and a stored rate another TV offered.
        assertEquals(fifty, select(SurfaceFrameRateChange.DISPLAY_MODE, 25f, 50f))
        assertNull(select(null, null, 120f))
        assertNull(select(null, null, null))
    }
}
