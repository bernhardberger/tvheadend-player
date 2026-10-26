package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelAspectStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun aChannelWithoutAnEntryUsesAutoAndEachChannelKeepsItsOwnMode() = runTest {
        val data = InMemoryPreferencesDataStore()
        AudioChoiceStore(data).profileIdentity()
        val store = ChannelAspectStore(data)
        assertEquals(AspectRatioMode.FIT, store.choices.first().modeFor(ChannelId(1)))

        store.set(ChannelId(1), AspectRatioMode.FORCE_16_9)
        store.set(ChannelId(2), AspectRatioMode.FORCE_4_3)

        val aspects = store.choices.first()
        assertEquals(AspectRatioMode.FORCE_16_9, aspects.modeFor(ChannelId(1)))
        assertEquals(AspectRatioMode.FORCE_4_3, aspects.modeFor(ChannelId(2)))
        assertEquals(AspectRatioMode.FIT, aspects.modeFor(ChannelId(3)))
        assertEquals("no channel", AspectRatioMode.FIT, aspects.modeFor(null))
    }

    @Test
    fun choosingAutoForgetsOnlyThatChannelsEntry() = runTest {
        val data = InMemoryPreferencesDataStore()
        AudioChoiceStore(data).profileIdentity()
        val store = ChannelAspectStore(data)
        store.set(ChannelId(1), AspectRatioMode.FORCE_16_9)
        store.set(ChannelId(2), AspectRatioMode.FORCE_4_3)

        store.set(ChannelId(1), AspectRatioMode.FIT)

        val aspects = store.choices.first()
        assertEquals(AspectRatioMode.FIT, aspects.modeFor(ChannelId(1)))
        assertEquals(AspectRatioMode.FORCE_4_3, aspects.modeFor(ChannelId(2)))
    }

    @Test
    fun aReplacedServerIdentityCannotInheritModesAndTheOldOnesReturnWithTheOldIdentity() = runTest {
        val data = InMemoryPreferencesDataStore()
        val audio = AudioChoiceStore(data)
        val original = audio.profileIdentity()
        val store = ChannelAspectStore(data)
        store.set(ChannelId(1), AspectRatioMode.FORCE_16_9)

        audio.profileIdentity(replace = true)
        assertEquals(AspectRatioMode.FIT, store.choices.first().modeFor(ChannelId(1)))
        store.set(ChannelId(1), AspectRatioMode.FORCE_4_3)
        assertEquals(AspectRatioMode.FORCE_4_3, store.choices.first().modeFor(ChannelId(1)))

        data.edit { it[stringPreferencesKey("explicit_audio_profile_v1")] = original }
        assertEquals(AspectRatioMode.FORCE_16_9, store.choices.first().modeFor(ChannelId(1)))
    }

    @Test
    fun withoutAServerIdentityNothingIsStoredAndEverythingIsAuto() = runTest {
        val store = ChannelAspectStore(InMemoryPreferencesDataStore())
        assertFalse(store.set(ChannelId(1), AspectRatioMode.FORCE_16_9))
        assertEquals(AspectRatioMode.FIT, store.choices.first().modeFor(ChannelId(1)))
    }

    @Test
    fun aChoiceReportsWhetherItWasStored() = runTest {
        val data = InMemoryPreferencesDataStore()
        AudioChoiceStore(data).profileIdentity()
        val store = ChannelAspectStore(data)
        assertTrue(store.set(ChannelId(1), AspectRatioMode.FORCE_16_9))
        assertTrue("forgetting an entry is stored too", store.set(ChannelId(1), AspectRatioMode.FIT))
    }

    @Test
    fun anUnreadableStoredValueReadsAsAutoAndIsReplacedByTheNextChoice() = runTest {
        val data = InMemoryPreferencesDataStore()
        AudioChoiceStore(data).profileIdentity()
        data.edit { it[stringPreferencesKey("channel_aspect_modes_v1")] = """[{"profile":"p","channel":1,"mode":"WIDEST"}]""" }
        val store = ChannelAspectStore(data)
        assertEquals(AspectRatioMode.FIT, store.choices.first().modeFor(ChannelId(1)))
        store.set(ChannelId(1), AspectRatioMode.FORCE_4_3)
        assertEquals(AspectRatioMode.FORCE_4_3, store.choices.first().modeFor(ChannelId(1)))
    }

    @Test
    fun retentionIsBoundedAndUpdatingAModeKeepsItRecent() = runTest {
        val data = InMemoryPreferencesDataStore()
        AudioChoiceStore(data).profileIdentity()
        val store = ChannelAspectStore(data)
        for (id in 1L..256L) store.set(ChannelId(id), AspectRatioMode.FORCE_16_9)
        store.set(ChannelId(1), AspectRatioMode.FORCE_4_3)
        store.set(ChannelId(257), AspectRatioMode.FORCE_16_9)

        val aspects = store.choices.first()
        assertEquals("oldest dropped", AspectRatioMode.FIT, aspects.modeFor(ChannelId(2)))
        assertEquals(AspectRatioMode.FORCE_4_3, aspects.modeFor(ChannelId(1)))
        assertEquals(AspectRatioMode.FORCE_16_9, aspects.modeFor(ChannelId(257)))
    }

    @Test
    fun modesSurviveClosingAndReopeningTheDiskStore() = runTest {
        val file = File(temporary.root, "aspect.preferences_pb")
        val firstJob = SupervisorJob()
        val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file })
        try {
            AudioChoiceStore(first).profileIdentity()
            ChannelAspectStore(first).set(ChannelId(5), AspectRatioMode.FORCE_16_9)
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        val second = PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file })
        try {
            val aspects = ChannelAspectStore(second).choices.first()
            assertEquals(AspectRatioMode.FORCE_16_9, aspects.modeFor(ChannelId(5)))
            assertEquals(AspectRatioMode.FIT, aspects.modeFor(ChannelId(6)))
        } finally {
            secondJob.cancelAndJoin()
        }
    }
}
