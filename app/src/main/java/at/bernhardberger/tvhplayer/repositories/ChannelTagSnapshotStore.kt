package at.bernhardberger.tvhplayer.repositories

internal data class ChannelTagMetadata(
    val id: Int,
    val name: String,
    val index: Int,
)

internal class ChannelTagSnapshotStore {
    private val tags = linkedMapOf<Int, ChannelTagMetadata>()
    private var initialSyncCompleted = false
    private var publishedTags: List<ChannelTagMetadata> = emptyList()

    fun reset(preservePublished: Boolean = true) {
        tags.clear()
        initialSyncCompleted = false
        if (!preservePublished) publishedTags = emptyList()
    }

    operator fun get(id: Int): ChannelTagMetadata? = tags[id]

    fun upsert(tag: ChannelTagMetadata): List<ChannelTagMetadata>? {
        tags[tag.id] = tag
        return snapshotIfReady()
    }

    fun delete(id: Int): List<ChannelTagMetadata>? {
        tags.remove(id)
        return snapshotIfReady()
    }

    fun completeInitialSync(): List<ChannelTagMetadata> {
        initialSyncCompleted = true
        return snapshot().also { publishedTags = it }
    }

    fun publishedSnapshot(): List<ChannelTagMetadata> = publishedTags

    private fun snapshot(): List<ChannelTagMetadata> =
        tags.values.sortedWith(compareBy({ it.index }, { it.name.lowercase() }, { it.id }))

    private fun snapshotIfReady(): List<ChannelTagMetadata>? = if (initialSyncCompleted) {
        snapshot().also { publishedTags = it }
    } else {
        null
    }
}
