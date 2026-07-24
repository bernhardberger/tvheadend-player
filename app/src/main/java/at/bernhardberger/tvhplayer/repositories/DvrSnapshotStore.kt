package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.htsp.DvrEntry

internal class DvrSnapshotStore {
    private val entries = linkedMapOf<Int, DvrEntry>()
    private var initialSyncCompleted = false
    private var publishedEntries: List<DvrEntry> = emptyList()

    fun reset(preservePublished: Boolean = true) {
        entries.clear()
        initialSyncCompleted = false
        if (!preservePublished) publishedEntries = emptyList()
    }

    operator fun get(id: Int): DvrEntry? = entries[id]

    fun upsert(entry: DvrEntry): List<DvrEntry>? {
        entries[entry.id] = entry
        return snapshotIfReady()
    }

    fun delete(id: Int): List<DvrEntry>? {
        entries.remove(id)
        return snapshotIfReady()
    }

    fun completeInitialSync(): List<DvrEntry> {
        initialSyncCompleted = true
        return snapshot().also { publishedEntries = it }
    }

    fun publishedSnapshot(): List<DvrEntry> = publishedEntries

    private fun snapshot(): List<DvrEntry> =
        entries.values.sortedWith(compareBy({ it.start }, { it.id }))

    private fun snapshotIfReady(): List<DvrEntry>? = if (initialSyncCompleted) {
        snapshot().also { publishedEntries = it }
    } else {
        null
    }
}
