package at.bernhardberger.tvhplayer.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
    private val alternate = AudioTrackChoice("alternate", "de", "audio/mpeg", 0, 2, 48000)

    @Test
    fun profileIdentitySurvivesStoreReconstructionButReplacementCannotInheritChoices() = runTest {
        val data = InMemoryPreferencesDataStore()
        val store = AudioChoiceStore(data)
        val original = store.profileIdentity()
        store.write(original, ChannelId(1), alternate)
        val reconstructed = AudioChoiceStore(data)
        assertEquals(original, reconstructed.profileIdentity())
        val replacement = reconstructed.profileIdentity(replace = true)
        assertNotEquals(original, replacement)
        assertNull(reconstructed.read(replacement, ChannelId(1)))
        assertEquals(alternate, reconstructed.read(original, ChannelId(1)))
    }

    @Test
    fun choiceSurvivesClosingAndReopeningTheDiskStoreWithIsolatedProfilesAndChannels() = runTest {
        val file = File(temporary.root, "audio.preferences_pb")
        val firstJob = SupervisorJob()
        val first = AudioChoiceStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file },
        ))
        try {
            first.write("profile-a", ChannelId(1), alternate)
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
    fun retentionIsBoundedAcrossProfilesAndUpdatingAChoiceKeepsItRecent() = runTest {
        val store = AudioChoiceStore(InMemoryPreferencesDataStore())
        for (id in 1L..64L) store.write("profile", ChannelId(id), alternate)
        store.write("profile", ChannelId(1), alternate.copy(language = "en"))
        store.write("other", ChannelId(1), alternate)
        assertNull(store.read("profile", ChannelId(2)))
        assertEquals(alternate.copy(language = "en"), store.read("profile", ChannelId(1)))
        assertEquals(alternate, store.read("other", ChannelId(1)))
    }
}
