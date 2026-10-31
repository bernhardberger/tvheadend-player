package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSettingsTest {
    @Test fun homeSectionsDefaultVisibleAndPersistIndependently() = kotlinx.coroutines.test.runTest {
        val data = InMemoryPreferencesDataStore()
        val store = UiSettingsStore(data)
        assertTrue(store.settings.first().hiddenHomeSections.isEmpty())
        val hidden = setOf(at.bernhardberger.tvhplayer.core.HomeSectionKind.RECENT_CHANNELS,
            at.bernhardberger.tvhplayer.core.HomeSectionKind.ON_NOW)
        hidden.forEach { store.setHomeSectionVisible(it, false) }
        assertEquals(hidden, UiSettingsStore(data).settings.first().hiddenHomeSections)
        hidden.forEach { store.setHomeSectionVisible(it, true) }
        assertTrue(UiSettingsStore(data).settings.first().hiddenHomeSections.isEmpty())
    }

    @Test fun delayedHomeSectionWritesDoNotOverwriteOtherVisibilityChanges() = kotlinx.coroutines.test.runTest {
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val data = InMemoryPreferencesDataStore(beforeUpdate = { release.await() })
        val store = UiSettingsStore(data)
        val first = launch { store.setHomeSectionVisible(at.bernhardberger.tvhplayer.core.HomeSectionKind.RECENT_CHANNELS, false) }
        val second = launch { store.setHomeSectionVisible(at.bernhardberger.tvhplayer.core.HomeSectionKind.ON_NOW, false) }
        runCurrent()
        assertTrue(store.settings.first().hiddenHomeSections.isEmpty())
        release.complete(Unit)
        first.join(); second.join()
        assertEquals(setOf(at.bernhardberger.tvhplayer.core.HomeSectionKind.RECENT_CHANNELS,
            at.bernhardberger.tvhplayer.core.HomeSectionKind.ON_NOW), store.settings.first().hiddenHomeSections)
    }

    @Test fun channelNumbersDefaultToOnAndPersistAcrossStoreRecreation() = kotlinx.coroutines.test.runTest {
        val data = InMemoryPreferencesDataStore()
        val store = UiSettingsStore(data)
        assertTrue(UiSettings().showChannelNumbers)
        assertTrue(store.settings.first().showChannelNumbers)
        store.setShowChannelNumbers(false)
        assertFalse(UiSettingsStore(data).settings.first().showChannelNumbers)
        store.setShowChannelNumbers(true)
        assertTrue(UiSettingsStore(data).settings.first().showChannelNumbers)
    }

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
    fun playbackAutoStart_isDisabledWhenNoPreferenceHasBeenSaved() {
        assertFalse(resolvePlaybackAutoStart(null))
    }

    @Test
    fun playbackAutoStart_respectsSavedPreference() {
        assertFalse(resolvePlaybackAutoStart(false))
        assertTrue(resolvePlaybackAutoStart(true))
    }

    @Test
    fun frameRateSwitching_isAlwaysByDefault() {
        val settings = PlayerSettings(
            audioLanguages = emptyList(),
            subtitleLanguage = null,
        )

        assertEquals(FrameRateSwitching.ALWAYS, settings.frameRateSwitching)
    }
}
