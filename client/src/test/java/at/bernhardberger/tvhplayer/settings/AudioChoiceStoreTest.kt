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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioChoiceStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val alternate = AudioTrackChoice("de", "audio/mpeg", 0, 2, 48000)

    @Test
    fun profileChoicesSurviveStoreReconstructionWithoutLeakingToAnotherProfile() = runTest {
        val data = InMemoryPreferencesDataStore()
        val store = AudioChoiceStore(data)
        val original = at.bernhardberger.tvheadend.sdk.core.ServerProfileId.generate().value
        data.edit { it[activeProfileKey] = original }
        store.write(original, ChannelId(1), alternate)
        val reconstructed = AudioChoiceStore(data)
        val replacement = at.bernhardberger.tvheadend.sdk.core.ServerProfileId.generate().value
        assertNotEquals(original, replacement)
        assertNull(reconstructed.read(replacement, ChannelId(1)))
        assertEquals(alternate, reconstructed.read(original, ChannelId(1)))
    }

    @Test
    fun choiceSurvivesClosingAndReopeningTheDiskStoreWithIsolatedProfilesAndChannels() = runTest {
        val file = File(temporary.root, "audio.preferences_pb")
        val firstJob = SupervisorJob()
        val firstData = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file },
        )
        val first = AudioChoiceStore(firstData)
        try {
            firstData.edit { it[activeProfileKey] = "profile-a" }
            first.write("profile-a", ChannelId(1), alternate)
            firstData.edit { it[activeProfileKey] = "profile-b" }
            first.write("profile-b", ChannelId(1), alternate.copy(language = "en"))
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        val second = AudioChoiceStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file },
        ))
        try {
            assertEquals(alternate, second.read("profile-a", ChannelId(1)))
            assertEquals(alternate.copy(language = "en"), second.read("profile-b", ChannelId(1)))
            assertNull(second.read("profile-a", ChannelId(2)))
            assertNull(second.read("profile-c", ChannelId(1)))
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test
    fun previouslyStoredEntriesWithAFormatIdReadAsNoChoiceInsteadOfFailing() = runTest {
        val data = InMemoryPreferencesDataStore()
        data.edit {
            it[stringPreferencesKey("explicit_audio_choices_v1")] =
                """[{"profile":"p","channel":1,"choice":{"id":"0","language":"de","mimeType":"audio/ac3","roleFlags":0,"channelCount":2,"sampleRate":48000}}]"""
        }
        val store = AudioChoiceStore(data)
        assertNull(store.read("p", ChannelId(1)))
        data.edit { it[activeProfileKey] = "p" }
        store.write("p", ChannelId(1), alternate)
        assertEquals(alternate, store.read("p", ChannelId(1)))
    }

    @Test
    fun retentionIsBoundedPerProfileAndUpdatingAChoiceKeepsItRecent() = runTest {
        val data = InMemoryPreferencesDataStore()
        val store = AudioChoiceStore(data)
        data.edit { it[activeProfileKey] = "profile" }
        for (id in 1L..64L) store.write("profile", ChannelId(id), alternate)
        store.write("profile", ChannelId(1), alternate.copy(language = "en"))
        store.write("profile", ChannelId(65), alternate)
        data.edit { it[activeProfileKey] = "other" }
        store.write("other", ChannelId(1), alternate)
        assertNull(store.read("profile", ChannelId(2)))
        assertEquals(alternate.copy(language = "en"), store.read("profile", ChannelId(1)))
        assertEquals(alternate, store.read("other", ChannelId(1)))
    }
}
