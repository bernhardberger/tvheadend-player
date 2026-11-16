package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ItemChoiceStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun aChoiceSurvivesClosingAndReopeningTheDiskStore() = runTest {
        val file = File(temporary.root, "choices.preferences_pb")
        val firstJob = SupervisorJob()
        val first = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + StandardTestDispatcher(testScheduler)), produceFile = { file },
        )
        val profile = try {
            selectProfile(first).also { store(first).set(it, 5, AdBreakMode.OFF) }
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        val second = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + StandardTestDispatcher(testScheduler)), produceFile = { file },
        )
        try {
            assertEquals(AdBreakMode.OFF, store(second).read(profile, 5))
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test
    fun legacyChannelFormatsRemainReadableAndUnknownAudioFieldsMeanNoChoice() = runTest {
        val data = InMemoryPreferencesDataStore()
        val profile = selectProfile(data)
        data.edit {
            it[stringPreferencesKey("channel_aspect_modes_v1")] =
                """[{"profile":"$profile","channel":5,"mode":"FORCE_16_9"}]"""
            it[stringPreferencesKey("explicit_audio_choices_v1")] =
                """[{"profile":"$profile","channel":1,"choice":{"language":"de","mimeType":"audio/mpeg","roleFlags":0,"channelCount":2,"sampleRate":48000}}]"""
        }
        val settings = PlayerSettingsStore(data)
        assertEquals(AspectRatioMode.FORCE_16_9, settings.channelAspects.choices.first()[5])
        val choice = AudioTrackChoice("de", "audio/mpeg", 0, 2, 48000)
        assertEquals(choice, settings.audioChoices.read(profile, 1))
        data.edit {
            it[stringPreferencesKey("explicit_audio_choices_v1")] =
                """[{"profile":"$profile","channel":1,"choice":{"id":"0","language":"de","mimeType":"audio/mpeg","roleFlags":0,"channelCount":2,"sampleRate":48000}}]"""
        }
        assertNull(settings.audioChoices.read(profile, 1))
    }

    @Test
    fun anItemWithoutAChoiceHasNoneAndEachItemKeepsItsOwn() = runTest {
        val data = InMemoryPreferencesDataStore()
        val profile = selectProfile(data)
        val store = store(data)
        assertNull(store.choices.first()[1])

        assertTrue(store.set(profile, 1, AdBreakMode.AUTOMATIC))
        assertTrue(store.set(profile, 2, AdBreakMode.OFF))

        val choices = store.choices.first()
        assertEquals(AdBreakMode.AUTOMATIC, choices[1])
        assertEquals(AdBreakMode.OFF, choices[2])
        assertNull(choices[3])
        assertNull("no item", choices[null])
    }

    @Test
    fun goingBackToTheDefaultForgetsOnlyThatItemsChoice() = runTest {
        val data = InMemoryPreferencesDataStore()
        val profile = selectProfile(data)
        val store = store(data)
        store.set(profile, 1, AdBreakMode.AUTOMATIC)
        store.set(profile, 2, AdBreakMode.OFF)

        assertTrue("forgetting is stored too", store.set(profile, 1, null))

        val choices = store.choices.first()
        assertNull(choices[1])
        assertEquals(AdBreakMode.OFF, choices[2])
    }

    @Test
    fun choicesBelongToTheirServerAndAChoiceForAReplacedServerIsDropped() = runTest {
        val data = InMemoryPreferencesDataStore()
        val original = selectProfile(data)
        val store = store(data)
        store.set(original, 1, AdBreakMode.AUTOMATIC)

        val replacementId = ServerProfileId.generate().also { PlayerSettingsStore(data).useProfile(it) }
        val replacement = replacementId.value
        assertNull(store.choices.first()[1])
        assertEquals("read is independent of the active server", AdBreakMode.AUTOMATIC, store.read(original, 1))
        assertNull(store.read(original, 2))
        assertNull(store.read(replacement, 1))
        assertFalse("captured before the switch", store.set(original, 1, AdBreakMode.OFF))
        assertFalse("forgetting also rejects a replaced server", store.set(original, 1, null))
        assertFalse("no server identity", store.set(null, 1, AdBreakMode.OFF))
        store.set(replacement, 1, AdBreakMode.ASK)
        assertEquals(AdBreakMode.ASK, store.choices.first()[1])
        assertEquals(AdBreakMode.ASK, store.read(replacement, 1))

        data.edit { it[activeProfileKey] = original }
        assertEquals(AdBreakMode.AUTOMATIC, store.choices.first()[1])

        // The settings store drops a removed server's recording choices and keeps the others'.
        val settings = PlayerSettingsStore(data)
        settings.recordingAdBreaks.set(original, 7, AdBreakMode.OFF)
        settings.useProfile(replacementId)
        settings.recordingAdBreaks.set(replacement, 7, AdBreakMode.AUTOMATIC)
        assertEquals(AdBreakMode.AUTOMATIC, settings.recordingAdBreaks.choices.first()[7])
        settings.removeProfile(replacementId)
        assertNull("removed with its server", settings.recordingAdBreaks.choices.first()[7])
        data.edit { it[activeProfileKey] = original }
        assertEquals(AdBreakMode.OFF, settings.recordingAdBreaks.choices.first()[7])
    }

    @Test
    fun anUnreadableStoredValueReadsAsNoChoiceAndIsReplacedByTheNextOne() = runTest {
        for (unreadable in listOf("""[{"profile":"p","item":1,"value":"SOMETIMES"}]""", """[{"profile":"p"}]""", "{}", "[")) {
            val data = InMemoryPreferencesDataStore()
            val profile = selectProfile(data)
            data.edit { it[stringPreferencesKey("choices")] = unreadable }
            val store = store(data)
            assertNull(unreadable, store.choices.first()[1])
            store.set(profile, 1, AdBreakMode.OFF)
            assertEquals(unreadable, AdBreakMode.OFF, store.choices.first()[1])
        }
        // One unreadable entry does not take the readable ones with it.
        val data = InMemoryPreferencesDataStore()
        val profile = selectProfile(data)
        data.edit {
            it[stringPreferencesKey("choices")] =
                """[{"profile":"$profile","item":1,"value":"SOMETIMES"},{"profile":"$profile","item":2,"value":"OFF"}]"""
        }
        val choices = store(data).choices.first()
        assertNull(choices[1])
        assertEquals(AdBreakMode.OFF, choices[2])
    }

    @Test
    fun retentionIsBoundedAndChangingAChoiceKeepsItRecent() = runTest {
        val data = InMemoryPreferencesDataStore()
        val profile = selectProfile(data)
        val store = store(data, maxEntries = 3)
        for (item in 1L..3L) store.set(profile, item, AdBreakMode.AUTOMATIC)
        store.set(profile, 1, AdBreakMode.OFF)
        store.set(profile, 4, AdBreakMode.AUTOMATIC)

        val choices = store.choices.first()
        assertNull("oldest dropped", choices[2])
        assertEquals(AdBreakMode.OFF, choices[1])
        assertEquals(AdBreakMode.AUTOMATIC, choices[4])
    }

    private fun store(data: DataStore<Preferences>, maxEntries: Int = 16) =
        ItemChoiceStore(data, "choices", "item", "value", AdBreakMode.serializer(), maxEntries)

    private suspend fun selectProfile(data: DataStore<Preferences>): String {
        val id = ServerProfileId.generate()
        PlayerSettingsStore(data).useProfile(id)
        return id.value
    }
}
