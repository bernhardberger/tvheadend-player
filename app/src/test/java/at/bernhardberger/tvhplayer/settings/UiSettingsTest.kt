package at.bernhardberger.tvhplayer.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSettingsTest {
    @Test
    fun epgMenu_isShownWhenNoPreferenceHasBeenSaved() {
        assertTrue(resolveEpgMenuVisibility(null))
    }

    @Test
    fun epgMenu_respectsSavedPreference() {
        assertFalse(resolveEpgMenuVisibility(false))
        assertTrue(resolveEpgMenuVisibility(true))
    }

    @Test
    fun playbackAutoStart_isEnabledWhenNoPreferenceHasBeenSaved() {
        assertTrue(resolvePlaybackAutoStart(null))
    }

    @Test
    fun playbackAutoStart_respectsSavedPreference() {
        assertFalse(resolvePlaybackAutoStart(false))
        assertTrue(resolvePlaybackAutoStart(true))
    }

    @Test
    fun refreshRateMatching_isEnabledByDefault() {
        val settings = PlayerSettings(
            audioLanguage = null,
            subtitleLanguage = null,
        )

        assertTrue(settings.refreshRateMatchingEnabled)
    }
}
