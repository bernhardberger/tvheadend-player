package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.emptyPreferences
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AudioPreferencesTest {
    @Test fun defaultsFollowDeviceAndSystemCaptions() {
        assertEquals(PlayerSettings(), playerSettingsFromPreferences(emptyPreferences()))
    }

    @Test fun languagesNormalizeDeduplicateAndLimit() {
        assertEquals(listOf("de", "en", "fr"), normalizeAudioLanguages(listOf("GER", "de-DE", "eng", "fra", "it")))
        assertEquals(emptyList<String>(), normalizeAudioLanguages(listOf("", "und")))
    }

    @Test fun slotReplacementMovesDuplicatesAndClearingShifts() {
        assertEquals(listOf("fr", "de", "en"), audioLanguagesWithSlot(listOf("de", "en", "fr"), 0, "fr"))
        assertEquals(listOf("de", "fr"), audioLanguagesWithSlot(listOf("de", "en", "fr"), 1, null))
        assertEquals(listOf("en", "de"), audioLanguagesWithSlot(listOf("de", "en"), 2, "de"))
    }

    @Test fun settersPersistAndSubtitleCanReturnToSystem() = runTest {
        val data = InMemoryPreferencesDataStore()
        val store = PlayerSettingsStore(data)
        store.setAudioLanguages(listOf("deu", "eng", "de"))
        store.setAudioFormat(AudioFormatPreference.PREFER_DOLBY)
        store.setAudioDescription(true)
        store.setSubtitleLanguage("fre")
        val saved = PlayerSettingsStore(data).playerSettings.first()
        assertEquals(listOf("de", "en"), saved.audioLanguages)
        assertEquals(AudioFormatPreference.PREFER_DOLBY, saved.audioFormat)
        assertTrue(saved.audioDescription)
        assertEquals("fr", saved.subtitleLanguage)
        store.setAudioLanguageSlot(0, null)
        store.setSubtitleLanguage(null)
        assertEquals(listOf("en"), store.playerSettings.first().audioLanguages)
        assertNull(store.playerSettings.first().subtitleLanguage)
    }

    @Test fun removingChoiceIsScopedAndDurable() = runTest {
        val data = InMemoryPreferencesDataStore()
        val store = AudioChoiceStore(data)
        val choice = AudioTrackChoice("de", "audio/ac3", 0, 6, 48000)
        store.write("first", ChannelId(1), choice)
        store.write("first", ChannelId(2), choice)
        store.write("second", ChannelId(1), choice)
        store.remove("first", ChannelId(1))
        val reopened = AudioChoiceStore(data)
        assertNull(reopened.read("first", ChannelId(1)))
        assertEquals(choice, reopened.read("first", ChannelId(2)))
        assertEquals(choice, reopened.read("second", ChannelId(1)))
        reopened.remove("first", ChannelId(1))
        assertEquals(choice, reopened.read("first", ChannelId(2)))
    }
}
