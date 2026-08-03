package at.bernhardberger.tvhplayer.testing

import at.bernhardberger.tvheadend.client.DvrRuntime
import at.bernhardberger.tvheadend.client.RecordingProgressCapability
import at.bernhardberger.tvheadend.client.RecordingProgressUpdateResult
import at.bernhardberger.tvheadend.core.DvrActionFailure
import at.bernhardberger.tvheadend.core.DvrActionResult
import at.bernhardberger.tvheadend.core.DvrConfig
import at.bernhardberger.tvheadend.core.DvrEntry
import at.bernhardberger.tvheadend.core.DvrFile
import at.bernhardberger.tvheadend.core.DvrState
import at.bernhardberger.tvheadend.core.RecordingWriteCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestDvrRuntime : DvrRuntime {
    private val workingEntries = linkedMapOf<Int, DvrEntry>()
    private val mutableEntries = MutableStateFlow<List<DvrEntry>>(emptyList())
    private val mutableEntriesReady = MutableStateFlow(false)
    private val mutableConfigs = MutableStateFlow<List<DvrConfig>>(emptyList())
    private val mutableWriteCapability = MutableStateFlow(RecordingWriteCapability.Unknown)
    private val mutableCanModifyRecordings = MutableStateFlow(false)

    override val entries: StateFlow<List<DvrEntry>> = mutableEntries
    override val entriesReady: StateFlow<Boolean> = mutableEntriesReady
    override val configs: StateFlow<List<DvrConfig>> = mutableConfigs
    override val writeCapability: StateFlow<RecordingWriteCapability> = mutableWriteCapability
    override val canModifyRecordings: StateFlow<Boolean> = mutableCanModifyRecordings
    override val progressCapability = MutableStateFlow(RecordingProgressCapability.Disconnected)

    fun applyAuthenticatedDvrAccess(dvrAccess: Boolean?) {
        val capability = when (dvrAccess) {
            true -> RecordingWriteCapability.Allowed
            false -> RecordingWriteCapability.Denied
            null -> return
        }
        mutableWriteCapability.value = capability
        mutableCanModifyRecordings.value = capability == RecordingWriteCapability.Allowed
    }

    suspend fun acceptDvrMessage(message: DvrTestMessage) {
        when (message.method) {
            "dvrEntryAdd", "dvrEntryUpdate" -> {
                val entry = mergeEntry(message) ?: return
                workingEntries[entry.id] = entry
                publishIfReady()
            }
            "dvrEntryDelete" -> {
                val id = message.int("id") ?: message.int("dvrId") ?: return
                workingEntries.remove(id)
                publishIfReady()
            }
            "initialSyncCompleted" -> {
                mutableEntriesReady.value = true
                publishEntries()
            }
        }
    }

    override fun entryForEvent(eventId: Int): DvrEntry? =
        entries.value.firstOrNull { entry -> entry.eventId == eventId }

    override suspend fun refreshConfigs() = Unit

    override suspend fun scheduleEvent(
        eventId: Int,
        configName: String?,
    ): DvrActionResult = DvrActionResult.Failed(DvrActionFailure.REJECTED)

    override suspend fun cancelEntry(entryId: Int): DvrActionResult =
        DvrActionResult.Failed(DvrActionFailure.REJECTED)

    override suspend fun deleteEntry(entryId: Int): DvrActionResult =
        DvrActionResult.Failed(DvrActionFailure.REJECTED)

    override suspend fun updateRecordingProgress(
        entryId: Int,
        playPositionSeconds: Long,
        setWatched: Boolean,
        timeoutMs: Long,
    ): RecordingProgressUpdateResult = RecordingProgressUpdateResult.Disconnected

    private fun publishIfReady() {
        if (entriesReady.value) publishEntries()
    }

    private fun publishEntries() {
        mutableEntries.value = workingEntries.values.sortedWith(compareBy({ it.start }, { it.id }))
    }

    private fun mergeEntry(message: DvrTestMessage): DvrEntry? {
        val id = message.int("id") ?: message.int("dvrId") ?: return null
        val existing = workingEntries[id]
        val error = message.str("error") ?: message.str("statusError") ?: existing?.failureReason
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
            state = testDvrState(
                message.str("state") ?: message.str("status") ?: existing?.state?.name,
                error,
            ),
            failureReason = error,
            configId = message.str("configId") ?: existing?.configId,
            files = message.list("files")?.mapNotNull(::testDvrFile) ?: existing?.files.orEmpty(),
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
}

data class DvrTestMessage(
    val method: String?,
    val seq: Int?,
    val fields: Map<String, Any?>,
) {
    fun int(name: String): Int? = (fields[name] as? Number)?.toInt()
    fun long(name: String): Long? = (fields[name] as? Number)?.toLong()
    fun str(name: String): String? = fields[name] as? String
    fun list(name: String): List<Any?>? = fields[name] as? List<Any?>
}

private fun testDvrState(value: String?, error: String? = null): DvrState {
    val normalized = value?.lowercase().orEmpty()
    val normalizedError = error?.lowercase().orEmpty()
    return when {
        "cancel" in normalized || "user" in normalizedError && "cancel" in normalizedError ->
            DvrState.CANCELLED
        "recording" in normalized || "running" in normalized -> DvrState.RECORDING
        "scheduled" in normalized || "pending" in normalized || "waiting" in normalized ->
            DvrState.SCHEDULED
        "completed" in normalized || "finished" in normalized ->
            if (error.isNullOrBlank()) DvrState.COMPLETED else DvrState.FAILED
        "failed" in normalized || "missed" in normalized || "invalid" in normalized ||
            error?.isNotBlank() == true -> DvrState.FAILED
        else -> DvrState.UNKNOWN
    }
}

private fun testDvrFile(value: Any?): DvrFile? {
    val fields = value as? Map<*, *> ?: return null
    return DvrFile(
        id = (fields["id"] as? Number)?.toInt(),
        path = fields["filename"] as? String ?: fields["path"] as? String,
        size = (fields["size"] as? Number)?.toLong(),
    )
}
