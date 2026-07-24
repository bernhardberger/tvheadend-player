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
}
