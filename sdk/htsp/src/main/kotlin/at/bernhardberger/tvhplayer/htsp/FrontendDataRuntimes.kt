package at.bernhardberger.tvhplayer.htsp

import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.RecordingWriteCapability
import kotlinx.coroutines.flow.StateFlow

interface ChannelEpgRuntime {
    val channelsUi: StateFlow<List<ChannelUi>>
    val tagsUi: StateFlow<List<ChannelTagUi>>
    val metadataReady: StateFlow<Boolean>

    fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>>
    fun nowEvent(channelId: Int, nowSec: Long): EpgEventEntry?
    fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry?
    fun requestEpgAtFrontier(channelIds: List<Int>, anchorSec: Long)
}

interface DvrRuntime {
    val entries: StateFlow<List<DvrEntry>>
    val entriesReady: StateFlow<Boolean>
    val configs: StateFlow<List<DvrConfig>>
    val writeCapability: StateFlow<RecordingWriteCapability>
    val canModifyRecordings: StateFlow<Boolean>
    val progressCapability: StateFlow<RecordingProgressCapability>

    fun entryForEvent(eventId: Int): DvrEntry?
    suspend fun refreshConfigs()
    suspend fun scheduleEvent(eventId: Int, configName: String? = null): DvrActionResult
    suspend fun cancelEntry(entryId: Int): DvrActionResult
    suspend fun deleteEntry(entryId: Int): DvrActionResult
    suspend fun updateRecordingProgress(
        entryId: Int,
        playPositionSeconds: Long,
        setWatched: Boolean,
        timeoutMs: Long = 2_000L,
    ): RecordingProgressUpdateResult
}
