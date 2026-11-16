package at.bernhardberger.tvhplayer.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemChoiceStoreTest {
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
        assertFalse("captured before the switch", store.set(original, 1, AdBreakMode.OFF))
        assertFalse("no server identity", store.set(null, 1, AdBreakMode.OFF))
        store.set(replacement, 1, AdBreakMode.ASK)
        assertEquals(AdBreakMode.ASK, store.choices.first()[1])

        data.edit { it[activeProfileKey] = original }
        assertEquals(AdBreakMode.AUTOMATIC, store.choices.first()[1])

        PlayerSettingsStore(data).removeProfile(replacementId)
        data.edit { it[activeProfileKey] = replacement }
        assertNull("removed with its server", PlayerSettingsStore(data).recordingAdBreaks.choices.first()[1])
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
