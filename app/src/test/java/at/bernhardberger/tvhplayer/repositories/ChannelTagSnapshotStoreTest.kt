package at.bernhardberger.tvhplayer.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelTagSnapshotStoreTest {
    @Test
    fun initialSyncPublishesTagsAtomicallyInServerOrder() {
        val store = ChannelTagSnapshotStore()

        assertNull(store.upsert(ChannelTagMetadata(id = 20, name = "Sport", index = 2)))
        assertNull(store.upsert(ChannelTagMetadata(id = 10, name = "News", index = 1)))
        assertEquals(listOf(10, 20), store.completeInitialSync().map { it.id })
    }

    @Test
    fun reconnectKeepsPublishedTagsUntilReplacementCompletes() {
        val store = ChannelTagSnapshotStore()
        store.upsert(ChannelTagMetadata(id = 10, name = "News", index = 1))
        store.completeInitialSync()

        store.reset(preservePublished = true)
        assertEquals(listOf(10), store.publishedSnapshot().map { it.id })
        assertNull(store.upsert(ChannelTagMetadata(id = 20, name = "Sport", index = 1)))
        assertEquals(listOf(20), store.completeInitialSync().map { it.id })
    }
}
