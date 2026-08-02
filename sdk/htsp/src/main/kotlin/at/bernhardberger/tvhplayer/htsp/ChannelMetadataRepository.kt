package at.bernhardberger.tvhplayer.htsp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

internal sealed interface ChannelMetadataEffect {
    data class ChannelUpserted(
        val channelId: Int,
        val isNew: Boolean,
    ) : ChannelMetadataEffect

    data class ChannelDeleted(val channelId: Int) : ChannelMetadataEffect

    data class InitialSyncCompleted(val channelIds: Set<Int>) : ChannelMetadataEffect
}

internal class ChannelMetadataRepository {
    private val lock = Any()
    private val channels = linkedMapOf<Int, ChannelUi>()
    private val tags = linkedMapOf<Int, ChannelTagUi>()
    private var initialSyncCompleted = false
    private var publishedChannels: List<ChannelUi> = emptyList()
    private var publishedTags: List<ChannelTagUi> = emptyList()
    private var channelsReady = CompletableDeferred<Unit>()

    private val _channelsUi = MutableStateFlow<List<ChannelUi>>(emptyList())
    val channelsUi: StateFlow<List<ChannelUi>> = _channelsUi

    private val _tagsUi = MutableStateFlow<List<ChannelTagUi>>(emptyList())
    val tagsUi: StateFlow<List<ChannelTagUi>> = _tagsUi

    private val _metadataReady = MutableStateFlow(false)
    val metadataReady: StateFlow<Boolean> = _metadataReady

    fun reset(preservePublished: Boolean = true) = synchronized(lock) {
        channels.clear()
        tags.clear()
        initialSyncCompleted = false
        if (!preservePublished) {
            publishedChannels = emptyList()
            publishedTags = emptyList()
        }
        channelsReady = CompletableDeferred()
        _channelsUi.value = publishedChannels
        _tagsUi.value = publishedTags
        _metadataReady.value = false
    }

    fun accept(
        message: HtspMessage,
        beforePublishing: (ChannelMetadataEffect) -> Unit = {},
    ): ChannelMetadataEffect? = synchronized(lock) {
        when (message.method) {
            "channelAdd", "channelUpdate" -> upsertChannel(message, beforePublishing)
            "channelDelete" -> deleteChannel(message, beforePublishing)
            "tagAdd", "tagUpdate" -> {
                upsertTag(message)
                null
            }
            "tagDelete" -> {
                deleteTag(message)
                null
            }
            "initialSyncCompleted" -> completeInitialSync(beforePublishing)
            else -> null
        }
    }

    fun currentChannelSnapshot(): List<ChannelUi> = synchronized(lock) {
        channelSnapshot()
    }

    suspend fun awaitChannelsReady(timeoutMs: Long = 30_000) {
        val readiness = synchronized(lock) { channelsReady }
        withTimeout(timeoutMs) { readiness.await() }
    }

    private fun upsertChannel(
        message: HtspMessage,
        beforePublishing: (ChannelMetadataEffect) -> Unit,
    ): ChannelMetadataEffect? {
        val id = message.int("channelId") ?: return null
        val existing = channels[id]
        val name = message.str("channelName") ?: existing?.name ?: return null
        val number = message.int("channelNumber")
            ?: message.int("number")
            ?: message.int("lcn")
            ?: message.int("channelNum")
            ?: message.int("channelno")
            ?: existing?.number
        val icon = message.str("channelIcon") ?: existing?.icon
        val rawTagIds = message.list("tagIds")
            ?: message.list("tags")
            ?: message.list("channelTags")
        val tagIds = rawTagIds
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?.toSet()
            ?: existing?.tagIds
            ?: emptySet()

        channels[id] = ChannelUi(id, name, number, icon, tagIds)
        val effect = ChannelMetadataEffect.ChannelUpserted(
            channelId = id,
            isNew = existing == null,
        )
        beforePublishing(effect)
        publishChannelsIfReady()
        return effect
    }

    private fun deleteChannel(
        message: HtspMessage,
        beforePublishing: (ChannelMetadataEffect) -> Unit,
    ): ChannelMetadataEffect? {
        val id = message.int("channelId") ?: return null
        channels.remove(id)
        val effect = ChannelMetadataEffect.ChannelDeleted(id)
        beforePublishing(effect)
        publishChannelsIfReady()
        return effect
    }

    private fun upsertTag(message: HtspMessage) {
        val id = message.int("tagId") ?: message.int("id") ?: return
        val existing = tags[id]
        val name = message.str("tagName") ?: message.str("name") ?: existing?.name ?: return
        val index = message.int("tagIndex")
            ?: message.int("index")
            ?: existing?.index
            ?: Int.MAX_VALUE
        tags[id] = ChannelTagUi(id, name, index)
        publishTagsIfReady()
    }

    private fun deleteTag(message: HtspMessage) {
        val id = message.int("tagId") ?: message.int("id") ?: return
        tags.remove(id)
        publishTagsIfReady()
    }

    private fun completeInitialSync(
        beforePublishing: (ChannelMetadataEffect) -> Unit,
    ): ChannelMetadataEffect {
        val channelSnapshot = channelSnapshot()
        val tagSnapshot = tagSnapshot()
        val effect = ChannelMetadataEffect.InitialSyncCompleted(
            channelIds = channelSnapshot.mapTo(linkedSetOf(), ChannelUi::id),
        )
        beforePublishing(effect)

        initialSyncCompleted = true
        publishChannels(channelSnapshot)
        publishTags(tagSnapshot)
        _metadataReady.value = true
        if (!channelsReady.isCompleted) channelsReady.complete(Unit)
        return effect
    }

    private fun publishChannelsIfReady() {
        if (initialSyncCompleted) publishChannels(channelSnapshot())
    }

    private fun publishTagsIfReady() {
        if (initialSyncCompleted) publishTags(tagSnapshot())
    }

    private fun publishChannels(snapshot: List<ChannelUi>) {
        publishedChannels = snapshot
        _channelsUi.value = snapshot
    }

    private fun publishTags(snapshot: List<ChannelTagUi>) {
        publishedTags = snapshot
        _tagsUi.value = snapshot
    }

    private fun channelSnapshot(): List<ChannelUi> =
        channels.values.sortedWith(CHANNEL_COMPARATOR)

    private fun tagSnapshot(): List<ChannelTagUi> =
        tags.values.sortedWith(TAG_COMPARATOR)

    private companion object {
        val CHANNEL_COMPARATOR = compareBy<ChannelUi>(
            { it.number == null },
            { it.number ?: Int.MAX_VALUE },
            { it.name.lowercase() },
            ChannelUi::id,
        )
        val TAG_COMPARATOR = compareBy<ChannelTagUi>(
            ChannelTagUi::index,
            { it.name.lowercase() },
            ChannelTagUi::id,
        )
    }
}
