package at.bernhardberger.tvhplayer.repositories

import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.dvrActionFailure
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrFile
import at.bernhardberger.tvhplayer.htsp.HtspEvent
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.htsp.dvrState
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DvrRepository(
    private val htsp: HtspService,
    ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val store = DvrSnapshotStore()
    private val _entries = MutableStateFlow<List<DvrEntry>>(emptyList())
    val entries: StateFlow<List<DvrEntry>> = _entries
    private val _configs = MutableStateFlow<List<DvrConfig>>(emptyList())
    val configs: StateFlow<List<DvrConfig>> = _configs
    /**
     * Whether this HTSP user may create, cancel, or delete recordings.
     * Starts true optimistically; latches false when the server denies DVR write
     * (`noaccess` / permission errors on getDvrConfigs or dvrEntry*).
     */
    private val _canModifyRecordings = MutableStateFlow(true)
    val canModifyRecordings: StateFlow<Boolean> = _canModifyRecordings

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            htsp.controlEvents.collect { event ->
                if (event is HtspEvent.ServerMessage) acceptDvrMessage(event.msg)
            }
        }
    }

    suspend fun onNewConnectionStarting(preservePublished: Boolean) {
        mutex.withLock {
            store.reset(preservePublished)
            _entries.value = store.publishedSnapshot()
            if (!preservePublished) _configs.value = emptyList()
            // Re-evaluate on each connection; do not carry a stale deny across users.
            _canModifyRecordings.value = true
        }
    }

    suspend fun refreshConfigs() {
        val reply = htsp.request(
            method = "getDvrConfigs",
            fields = emptyMap(),
            timeoutMs = 10_000,
            disconnectOnTimeout = false,
        )
        val failure = dvrActionFailure(reply.fields)
        if (failure == DvrActionFailure.PERMISSION_DENIED) {
            _canModifyRecordings.value = false
            _configs.value = emptyList()
            return
        }
        _configs.value = dvrConfigsFromReply(reply)
    }

    internal suspend fun acceptDvrMessage(message: HtspMessage) {
        mutex.withLock {
            when (message.method) {
                "dvrEntryAdd", "dvrEntryUpdate" -> {
                    val entry = mergeDvrEntry(message, store) ?: return@withLock
                    store.upsert(entry)?.let { _entries.value = it }
                }
                "dvrEntryDelete" -> {
                    val id = message.int("id") ?: message.int("dvrId") ?: return@withLock
                    store.delete(id)?.let { _entries.value = it }
                }
                "initialSyncCompleted" -> _entries.value = store.completeInitialSync()
            }
        }
    }

    fun entryForEvent(eventId: Int): DvrEntry? =
        entries.value.firstOrNull { it.eventId == eventId }

    suspend fun scheduleEvent(eventId: Int, configId: String? = null): DvrActionResult =
        performAction(
            method = "dvrEntryAdd",
            fields = buildMap {
                put("eventId", eventId)
                if (configId != null) put("configId", configId)
            },
        )

    suspend fun cancelEntry(entryId: Int): DvrActionResult = performAction(
        method = "dvrEntryCancel",
        fields = mapOf("id" to entryId),
    )

    suspend fun deleteEntry(entryId: Int): DvrActionResult = performAction(
        method = "dvrEntryDelete",
        fields = mapOf("id" to entryId),
    )

    private suspend fun performAction(
        method: String,
        fields: Map<String, Any?>,
    ): DvrActionResult = try {
        val reply = htsp.request(
            method = method,
            fields = fields,
            timeoutMs = 10_000,
            disconnectOnTimeout = false,
        )
        val failure = dvrActionFailure(reply.fields)
        if (failure == null) {
            DvrActionResult.Accepted(reply.int("id") ?: reply.int("dvrId"))
        } else {
            if (failure == DvrActionFailure.PERMISSION_DENIED) {
                _canModifyRecordings.value = false
            }
            DvrActionResult.Failed(failure)
        }
    } catch (_: SocketTimeoutException) {
        DvrActionResult.Failed(DvrActionFailure.CONNECTION)
    } catch (_: ConnectException) {
        DvrActionResult.Failed(DvrActionFailure.CONNECTION)
    } catch (_: Exception) {
        DvrActionResult.Failed(DvrActionFailure.REJECTED)
    }

    private fun mergeDvrEntry(message: HtspMessage, store: DvrSnapshotStore): DvrEntry? {
        val id = message.int("id") ?: message.int("dvrId") ?: return null
        val existing = store[id]
        val error = message.str("error") ?: message.str("statusError") ?: existing?.failureReason
        return DvrEntry(
            id = id,
            eventId = message.int("eventId") ?: existing?.eventId,
            channelId = message.int("channelId") ?: message.int("channel")
                ?: existing?.channelId ?: 0,
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
            playPosition = message.long("playposition") ?: message.long("playPosition")
                ?: existing?.playPosition,
            playCount = message.int("playcount") ?: message.int("playCount")
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

    private fun dvrFile(value: Any?): DvrFile? {
        val fields = value as? Map<*, *> ?: return null
        return DvrFile(
            id = (fields["id"] as? Number)?.toInt(),
            path = fields["filename"] as? String ?: fields["path"] as? String,
            size = (fields["size"] as? Number)?.toLong(),
        )
    }
}

internal fun dvrConfigsFromReply(reply: HtspMessage): List<DvrConfig> {
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
                is Number -> enabled.toInt() != 0
                is String -> enabled != "0" && !enabled.equals("false", ignoreCase = true)
                else -> true
            },
        )
    }
}
