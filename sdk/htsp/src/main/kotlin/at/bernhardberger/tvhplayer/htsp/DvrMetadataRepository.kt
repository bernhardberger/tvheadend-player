package at.bernhardberger.tvhplayer.htsp

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class DvrMetadataRepository {
    private val lock = Any()
    private val workingEntries = linkedMapOf<Int, DvrEntry>()
    private var initialSyncCompleted = false
    private var publicationVersion = 0L
    private var publishedEntries: List<DvrEntry> = emptyList()
    private var publishedEntriesReady = false
    private var publishedConfigs: List<DvrConfig> = emptyList()
    private val published = MutableStateFlow(PublishedMetadata())

    val entries: StateFlow<List<DvrEntry>> = published.mapState(PublishedMetadata::entries)
    val entriesReady: StateFlow<Boolean> = published.mapState(PublishedMetadata::entriesReady)
    val configs: StateFlow<List<DvrConfig>> = published.mapState(PublishedMetadata::configs)

    fun accept(message: HtspMessage) {
        val publication = synchronized(lock) {
            when (message.method) {
                "dvrEntryAdd", "dvrEntryUpdate" -> {
                    val entry = mergeEntryLocked(message) ?: return@synchronized null
                    workingEntries[entry.id] = entry
                    stageWorkingIfReadyLocked()
                }

                "dvrEntryDelete" -> {
                    val id = message.int("id") ?: message.int("dvrId")
                        ?: return@synchronized null
                    workingEntries.remove(id)
                    stageWorkingIfReadyLocked()
                }

                "initialSyncCompleted" -> {
                    initialSyncCompleted = true
                    publishedEntries = workingSnapshotLocked()
                    publishedEntriesReady = true
                    stagePublicationLocked()
                }

                else -> null
            }
        }
        publish(publication)
    }

    fun reset(preservePublished: Boolean = true) {
        val publication = synchronized(lock) {
            workingEntries.clear()
            initialSyncCompleted = false
            if (!preservePublished) {
                publishedEntries = emptyList()
                publishedEntriesReady = false
                publishedConfigs = emptyList()
                stagePublicationLocked()
            } else {
                null
            }
        }
        publish(publication)
    }

    fun ingestDvrConfigsReply(reply: HtspMessage) {
        val configs = dvrConfigs(reply)
        val publication = synchronized(lock) {
            publishedConfigs = configs
            stagePublicationLocked()
        }
        publish(publication)
    }

    fun clearConfigs() {
        val publication = synchronized(lock) {
            publishedConfigs = emptyList()
            stagePublicationLocked()
        }
        publish(publication)
    }

    fun entryForEvent(eventId: Int): DvrEntry? = synchronized(lock) {
        publishedEntries.firstOrNull { it.eventId == eventId }
    }

    private fun mergeEntryLocked(message: HtspMessage): DvrEntry? {
        val id = message.int("id") ?: message.int("dvrId") ?: return null
        val existing = workingEntries[id]
        val error = message.str("error")
            ?: message.str("statusError")
            ?: existing?.failureReason
        return DvrEntry(
            id = id,
            eventId = message.int("eventId") ?: existing?.eventId,
            channelId = message.int("channelId")
                ?: message.int("channel")
                ?: existing?.channelId
                ?: 0,
            start = message.long("start") ?: existing?.start ?: 0L,
            stop = message.long("stop") ?: existing?.stop ?: 0L,
            title = message.str("title") ?: existing?.title ?: "—",
            subtitle = message.str("subtitle") ?: existing?.subtitle,
            summary = message.str("summary") ?: existing?.summary,
            description = message.str("description") ?: existing?.description,
            state = dvrState(
                message.str("state") ?: message.str("status") ?: existing?.state?.name,
                error,
            ),
            failureReason = error,
            configId = message.str("configId") ?: existing?.configId,
            files = message.list("files")?.mapNotNull(::dvrFile) ?: existing?.files.orEmpty(),
            owner = message.str("owner") ?: existing?.owner,
            creator = message.str("creator") ?: existing?.creator,
            path = message.str("path") ?: existing?.path,
            channelName = message.str("channelName") ?: existing?.channelName,
            image = message.str("image") ?: existing?.image,
            fanartImage = message.str("fanartImage") ?: existing?.fanartImage,
            playPosition = message.long("playposition")
                ?: message.long("playPosition")
                ?: existing?.playPosition,
            playCount = message.int("playcount")
                ?: message.int("playCount")
                ?: existing?.playCount,
            seasonNumber = message.int("seasonNumber") ?: existing?.seasonNumber,
            episodeNumber = message.int("episodeNumber") ?: existing?.episodeNumber,
            episodeCount = message.int("episodeCount") ?: existing?.episodeCount,
            partNumber = message.int("partNumber") ?: existing?.partNumber,
            partCount = message.int("partCount") ?: existing?.partCount,
            autorecId = message.str("autorecId") ?: existing?.autorecId,
            timerecId = message.str("timerecId") ?: existing?.timerecId,
        )
    }

    private fun stageWorkingIfReadyLocked(): PublishedMetadata? {
        if (!initialSyncCompleted) return null
        publishedEntries = workingSnapshotLocked()
        return stagePublicationLocked()
    }

    private fun workingSnapshotLocked(): List<DvrEntry> =
        workingEntries.values.sortedWith(compareBy({ it.start }, { it.id }))

    private fun stagePublicationLocked(): PublishedMetadata = PublishedMetadata(
        version = ++publicationVersion,
        entries = publishedEntries,
        entriesReady = publishedEntriesReady,
        configs = publishedConfigs,
    )

    private fun publish(publication: PublishedMetadata?) {
        if (publication == null) return
        published.update { current ->
            if (publication.version > current.version) publication else current
        }
    }
}

private data class PublishedMetadata(
    val version: Long = 0L,
    val entries: List<DvrEntry> = emptyList(),
    val entriesReady: Boolean = false,
    val configs: List<DvrConfig> = emptyList(),
)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R,
) : StateFlow<R> {
    override val value: R
        get() = transform(source.value)

    override val replayCache: List<R>
        get() = source.replayCache.map(transform)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        source.map(transform).distinctUntilChanged().collect(collector)
        error("StateFlow collection completed unexpectedly")
    }
}

private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
    MappedStateFlow(this, transform)

private fun dvrFile(value: Any?): DvrFile? {
    val fields = value as? Map<*, *> ?: return null
    return DvrFile(
        id = (fields["id"] as? Number)?.toInt(),
        path = fields["filename"] as? String ?: fields["path"] as? String,
        size = (fields["size"] as? Number)?.toLong(),
    )
}

private fun dvrConfigs(reply: HtspMessage): List<DvrConfig> {
    val raw = reply.list("dvrconfigs") ?: reply.list("configs") ?: emptyList()
    return raw.mapNotNull { value ->
        val fields = value as? Map<*, *> ?: return@mapNotNull null
        val id = listOf("uuid", "configId", "id")
            .firstNotNullOfOrNull { fields[it]?.toString()?.takeIf(String::isNotBlank) }
            ?: return@mapNotNull null
        val name = listOf("name", "profileName")
            .firstNotNullOfOrNull { fields[it]?.toString()?.takeIf(String::isNotBlank) }
            ?: id
        DvrConfig(
            id = id,
            name = name,
            comment = fields["comment"]?.toString()?.takeIf(String::isNotBlank),
            enabled = when (val enabled = fields["enabled"]) {
                is Boolean -> enabled
                is Number -> enabled.toDouble() != 0.0
                is String -> enabled != "0" && !enabled.equals("false", ignoreCase = true)
                else -> true
            },
        )
    }
}
