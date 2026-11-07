package at.bernhardberger.tvhplayer.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSettingsTest {
    @Test fun storedWhatsNewMarkerIsHiddenUntilLoadedAndForCurrentOrNewerVersions() {
        assertFalse(hasUnseenWhatsNew(null, 10))
        assertTrue(hasUnseenWhatsNew(9, 10))
        assertFalse(hasUnseenWhatsNew(10, 10))
        assertFalse(hasUnseenWhatsNew(11, 10))
    }

    @Test fun whatsNewPolicyDistinguishesFreshInstallUpdateAndSeenVersions() {
        assertFalse(whatsNewUnseen(null, 10, 100, 100))
        assertTrue(whatsNewUnseen(null, 10, 100, 200))
        assertTrue(whatsNewUnseen(9, 10, 100, 200))
        assertFalse(whatsNewUnseen(10, 10, 100, 200))
        assertFalse(whatsNewUnseen(11, 10, 100, 200))
    }

    @Test fun whatsNewInitializationAndOpeningPersistAppWide() = kotlinx.coroutines.test.runTest {
        val fresh = UiSettingsStore(InMemoryPreferencesDataStore())
        fresh.initializeWhatsNew(10, 100, 100)
        assertEquals(10, fresh.settings.first().whatsNewSeenVersion)
        val data = InMemoryPreferencesDataStore()
        val updated = UiSettingsStore(data)
        updated.initializeWhatsNew(10, 100, 200)
        assertEquals(0, updated.settings.first().whatsNewSeenVersion)
        updated.markWhatsNewSeen(10)
        val recreated = UiSettingsStore(data)
        recreated.initializeWhatsNew(11, 100, 300)
        assertEquals(10, recreated.settings.first().whatsNewSeenVersion)
        assertTrue(whatsNewUnseen(recreated.settings.first().whatsNewSeenVersion, 11, 100, 300))
    }

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
