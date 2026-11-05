package at.bernhardberger.tvhplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDisplayModeTest {
    private val current = DisplayModeSpec(1920, 1080, 60f, 1)

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
}
