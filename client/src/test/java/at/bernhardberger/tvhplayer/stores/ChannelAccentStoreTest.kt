package at.bernhardberger.tvhplayer.stores

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelAccentStoreTest {
    @get:Rule val folder = TemporaryFolder()

    /** One store over [file]; DataStore allows one at a time, so [use] ends before the next opens. */
    private fun <T> withStore(file: File, use: suspend (ChannelAccentStore) -> T): T = runBlocking {
        val job = Job()
        val store = ChannelAccentStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        try { use(store) } finally { job.cancelAndJoin() }
    }

    @Test fun aSecondStoreOverTheSameFileReturnsTheColourOnceLoaded() {
        val file = File(folder.root, "accents.preferences_pb")
        withStore(file) { store ->
            store.load()
            store.put("first", 0x336699)
            store.put("neutral", 0x2E3440)
            assertEquals(0x336699, store["first"])
        }
        withStore(file) { store ->
            assertNull("nothing is known before the file was read", store["first"])
            store.load()
            assertEquals(0x336699, store["first"])
            assertEquals(0x2E3440, store["neutral"])
            assertNull(store["other"])
        }
    }

    @Test fun aColourPutWhileTheFileIsUnreadWinsOverTheFiles() {
        val file = File(folder.root, "accents.preferences_pb")
        withStore(file) { store -> store.put("key", 0x111111) }
        withStore(file) { store ->
            store.put("key", 0x222222)
            store.load()
            assertEquals(0x222222, store["key"])
        }
    }

    @Test fun clearEmptiesMemoryAndTheFileAndCounts() {
        val file = File(folder.root, "accents.preferences_pb")
        withStore(file) { store ->
            store.put("key", 0x336699)
            assertEquals(0, store.clears.value)
            store.clear()
            assertNull(store["key"])
            assertEquals(1, store.clears.value)
        }
        withStore(file) { store ->
            store.load()
            assertNull("the file was emptied too", store["key"])
        }
    }

    @Test fun aClearBeforeTheFileWasReadLeavesNothingToReadBack() {
        val file = File(folder.root, "accents.preferences_pb")
        withStore(file) { store -> store.put("key", 0x336699) }
        withStore(file) { store ->
            store.clear()
            store.load()
            assertNull(store["key"])
        }
    }

    @Test fun withoutAFileColoursStayInMemory() = runBlocking {
        val store = ChannelAccentStore()
        store.load()
        store.put("key", 0x336699)
        assertEquals(0x336699, store["key"])
        store.clear()
        assertNull(store["key"])
    }
}
