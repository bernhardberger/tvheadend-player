package at.bernhardberger.tvhplayer.data

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrPlaybackProgress
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.EpgCoverageRequestResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability as SdkProgressCapability
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionOperationFailure
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TimerecRule
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import java.lang.Math.toIntExact
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class EpgFrontierResult { SETTLED, UNAVAILABLE }

interface ChannelEpgRuntime {
    val channels: StateFlow<List<Channel>>
    val channelTags: StateFlow<List<ChannelTag>>
    val metadataReady: StateFlow<Boolean>
    fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>>
    fun nowEvent(channelId: Int, nowSec: Long): EpgEventEntry?
    fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry?
    fun requestEpgAtFrontier(channelIds: List<Int>, anchorSec: Long): EpgFrontierResult
}

interface DvrRuntime {
    val entries: StateFlow<List<DvrEntry>>
    val entriesReady: StateFlow<Boolean>
    val timeRecordingRules: StateFlow<List<DvrTimeRecordingRule>>
    val timeRecordingRulesReady: StateFlow<Boolean>
    val configs: StateFlow<List<DvrConfig>>
    val canModifyRecordings: StateFlow<Boolean>
    val progressCapability: StateFlow<RecordingProgressCapability>
    fun entryForEvent(eventId: Int): DvrEntry?
    suspend fun refreshConfigs()
    suspend fun scheduleEvent(eventId: Int, configName: String? = null): DvrActionResult
    suspend fun cancelEntry(entryId: Int): DvrActionResult
    suspend fun stopEntry(entryId: Int): DvrActionResult
    suspend fun deleteEntry(entryId: Int): DvrActionResult
    suspend fun updateRecordingProgress(
        entryId: Int,
        playPositionSeconds: Long,
        setWatched: Boolean,
        timeoutMs: Long = 2_000L,
    ): RecordingProgressUpdateResult
}

class TvheadendDataRuntime(
    val session: TvheadendSession,
    private val scope: CoroutineScope,
) : ChannelEpgRuntime, DvrRuntime {
    private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
        map(transform).stateIn(scope, SharingStarted.Eagerly, transform(value))

    val connectionState: StateFlow<ConnectionState> = session.state.mapState(::connectionState)
    val connectionFailure: StateFlow<ConnectionFailureKind?> = session.state.mapState { state ->
        (state as? SessionState.Unavailable)?.reason?.toFailureKind()
    }
    override val channels = session.channelRepository.channels.mapState { list -> list.mapNotNull { it.toApp() } }
    override val channelTags = session.channelRepository.tags.mapState { list -> list.mapNotNull { it.toApp() } }
    override val metadataReady = session.channelRepository.state.mapState { it is ChannelRepositoryState.Current }
    override val entries = session.dvrRepository.entries.mapState { list -> list.mapNotNull { it.toApp() } }
    override val entriesReady = session.dvrRepository.state.mapState { it is DvrRepositoryState.Current }
    override val timeRecordingRules = session.dvrRepository.timerecRules.mapState { list -> list.mapNotNull { it.toApp() } }
    override val timeRecordingRulesReady = entriesReady
    override val configs = session.dvrRepository.configurations.mapState { list ->
        list.map { DvrConfig(it.id.value, it.name, it.comment) }
    }
    override val canModifyRecordings = session.state.mapState { state ->
        (state as? SessionState.Ready)?.capabilities?.dvrWrite == CapabilityAccess.ALLOWED
    }
    override val progressCapability = combine(
        session.recordingProgressCapability,
        session.state,
    ) { capability, state ->
        appRecordingProgressCapability(
            capability,
            (state as? SessionState.Ready)?.capabilities?.dvrWrite ?: CapabilityAccess.UNKNOWN,
        )
    }.stateIn(scope, SharingStarted.Eagerly, RecordingProgressCapability.Disconnected)

    private val epgByChannel = ConcurrentHashMap<Int, StateFlow<List<EpgEventEntry>>>()

    override fun epgForChannel(channelId: Int): StateFlow<List<EpgEventEntry>> =
        epgByChannel.getOrPut(channelId) {
            session.epgRepository.events(ChannelId(channelId.toLong()))
                .map { events -> events.mapNotNull { it.toApp() } }
                .stateIn(scope, SharingStarted.Eagerly, emptyList())
        }

    override fun nowEvent(channelId: Int, nowSec: Long): EpgEventEntry? =
        epgForChannel(channelId).value.firstOrNull { nowSec >= it.start && nowSec < it.stop }

    override fun nextEvent(channelId: Int, nowSec: Long): EpgEventEntry? =
        epgForChannel(channelId).value.firstOrNull { it.start >= nowSec }

    override fun requestEpgAtFrontier(channelIds: List<Int>, anchorSec: Long): EpgFrontierResult {
        val through = Instant.fromEpochSeconds(anchorSec)
        return requestAllEpgCoverage(channelIds) { id ->
            session.epgRepository.requestCoverage(ChannelId(id.toLong()), through)
        }
    }

    override fun entryForEvent(eventId: Int): DvrEntry? = entries.value.firstOrNull { it.eventId == eventId }
    override suspend fun refreshConfigs() = Unit

    override suspend fun scheduleEvent(eventId: Int, configName: String?): DvrActionResult {
        val configId = configName?.let { name -> configs.value.firstOrNull { it.name == name }?.id }
        return session.dvrRepository.scheduleEntry(
            DvrScheduleRequest(
                schedule = DvrSchedule.Programme(EventId(eventId.toLong())),
                configId = configId?.let(::DvrConfigId),
            ),
        ).toActionResult()
    }

    override suspend fun cancelEntry(entryId: Int) =
        session.dvrRepository.cancelEntry(DvrEntryId(entryId.toLong())).toActionResult()

    override suspend fun stopEntry(entryId: Int) =
        session.dvrRepository.stopEntry(DvrEntryId(entryId.toLong())).toActionResult()

    override suspend fun deleteEntry(entryId: Int) =
        session.dvrRepository.deleteEntry(DvrEntryId(entryId.toLong())).toActionResult()

    override suspend fun updateRecordingProgress(
        entryId: Int,
        playPositionSeconds: Long,
        setWatched: Boolean,
        timeoutMs: Long,
    ): RecordingProgressUpdateResult = session.dvrRepository.reportProgress(
        DvrEntryId(entryId.toLong()),
        DvrPlaybackProgress(playPositionSeconds.seconds, setWatched),
    ).toUpdateResult()
}

internal fun requestAllEpgCoverage(
    channelIds: List<Int>,
    request: (Int) -> EpgCoverageRequestResult,
): EpgFrontierResult {
    val outcomes = channelIds.map(request)
    return if (outcomes.any {
        it == EpgCoverageRequestResult.ACCEPTED || it == EpgCoverageRequestResult.SATISFIED
    }) {
        EpgFrontierResult.SETTLED
    } else {
        EpgFrontierResult.UNAVAILABLE
    }
}

internal fun appRecordingProgressCapability(
    capability: SdkProgressCapability,
    dvrWrite: CapabilityAccess,
): RecordingProgressCapability = when (capability) {
    SdkProgressCapability.UNKNOWN -> RecordingProgressCapability.Disconnected
    SdkProgressCapability.UNSUPPORTED -> RecordingProgressCapability.Unsupported
    SdkProgressCapability.SUPPORTED -> when (dvrWrite) {
        CapabilityAccess.ALLOWED -> RecordingProgressCapability.Full
        CapabilityAccess.DENIED -> RecordingProgressCapability.ReadOnly
        CapabilityAccess.UNKNOWN -> RecordingProgressCapability.Disconnected
    }
}

private fun at.bernhardberger.tvheadend.sdk.core.Channel.toApp(): Channel? = runCatching {
    Channel(
        channelId = toIntExact(id.value),
        name = name.orEmpty(),
        number = number?.let(::toIntExact),
        icon = icon,
        tagIds = tagIds.orEmpty().mapTo(linkedSetOf()) { toIntExact(it.value) },
        currentEventId = currentEventId?.value?.let(::toIntExact),
        nextEventId = nextEventId?.value?.let(::toIntExact),
    )
}.getOrNull()

private fun at.bernhardberger.tvheadend.sdk.core.ChannelTag.toApp(): ChannelTag? = runCatching {
    ChannelTag(toIntExact(id.value), name.orEmpty(), index?.let(::toIntExact) ?: 0)
}.getOrNull()

private fun at.bernhardberger.tvheadend.sdk.core.EpgEvent.toApp(): EpgEventEntry? = runCatching {
    val channel = channelId ?: return null
    EpgEventEntry(
        eventId = toIntExact(id.value),
        channelId = toIntExact(channel.value),
        start = start.epochSeconds,
        stop = stop.epochSeconds,
        title = title.orEmpty(),
        summary = summary,
        description = description,
        genre = genre,
        contentType = contentType?.let(::toIntExact),
        seasonNumber = episode?.seasonNumber?.let(::toIntExact),
        episodeNumber = episode?.episodeNumber?.let(::toIntExact),
        episodeCount = episode?.episodeCount?.let(::toIntExact),
        partNumber = episode?.partNumber?.let(::toIntExact),
        partCount = episode?.partCount?.let(::toIntExact),
        image = image,
        dvrId = dvrEntryId?.value?.let(::toIntExact),
    )
}.getOrNull()

private fun at.bernhardberger.tvheadend.sdk.core.DvrEntry.toApp(): DvrEntry? = runCatching {
    DvrEntry(
        id = toIntExact(id.value),
        eventId = eventId?.value?.let(::toIntExact),
        channelId = channelId?.value?.let(::toIntExact) ?: 0,
        start = start?.epochSeconds ?: 0L,
        stop = stop?.epochSeconds ?: 0L,
        title = title.orEmpty(),
        subtitle = subtitle,
        summary = summary,
        description = description,
        state = state.toAppState(),
        failureReason = state?.takeIf { it.name.endsWith("ERROR") }?.name,
        configId = configId?.value,
        files = files.orEmpty().map { DvrFile(it.fileId?.let(::toIntExact), it.path, it.sizeBytes) },
        owner = owner,
        creator = creator,
        path = path,
        channelName = channelName,
        image = image,
        fanartImage = fanartImage,
        playPosition = playPosition?.inWholeSeconds,
        playCount = playCount?.let(::toIntExact),
        seasonNumber = episode?.seasonNumber?.let(::toIntExact),
        episodeNumber = episode?.episodeNumber?.let(::toIntExact),
        episodeCount = episode?.episodeCount?.let(::toIntExact),
        partNumber = episode?.partNumber?.let(::toIntExact),
        partCount = episode?.partCount?.let(::toIntExact),
        autorecId = autorecRuleId?.value,
        timerecId = timerecRuleId?.value,
        dataSizeBytes = dataSizeBytes,
        enabled = enabled,
        contentType = contentType?.let(::toIntExact),
        subscriptionError = subscriptionError?.name,
        startExtraMinutes = startExtraMinutes,
        stopExtraMinutes = stopExtraMinutes,
    )
}.getOrNull()

private fun TimerecRule.toApp(): DvrTimeRecordingRule? = runCatching {
    DvrTimeRecordingRule(
        id = id.value,
        enabled = enabled ?: true,
        name = name.orEmpty(),
        title = title.orEmpty(),
        channelId = channelId?.value?.let(::toIntExact) ?: 0,
        startMinutesSinceMidnight = startMinutesSinceMidnight ?: 0,
        stopMinutesSinceMidnight = stopMinutesSinceMidnight ?: 0,
        daysOfWeekMask = daysOfWeekMask,
        priority = priority,
        retentionDays = retentionDays,
        directory = directory,
        owner = owner,
        creator = creator,
        configId = configId?.value,
        comment = comment,
    )
}.getOrNull()

private fun DvrEntryState?.toAppState(): DvrState = when (this) {
    DvrEntryState.SCHEDULED -> DvrState.SCHEDULED
    DvrEntryState.RECORDING -> DvrState.RECORDING
    DvrEntryState.COMPLETED -> DvrState.COMPLETED
    DvrEntryState.MISSED,
    DvrEntryState.INVALID,
    DvrEntryState.RECORDING_ERROR,
    DvrEntryState.COMPLETED_ERROR,
    DvrEntryState.FILE_MISSING -> DvrState.FAILED
    DvrEntryState.UNKNOWN, null -> DvrState.UNKNOWN
}

private fun SessionState.toFailureKind(): ConnectionFailureKind? = (this as? SessionState.Unavailable)?.reason?.toFailureKind()

private fun SessionFailure.toFailureKind(): ConnectionFailureKind = when (this) {
    SessionFailure.AuthenticationRejected -> ConnectionFailureKind.AUTHENTICATION
    SessionFailure.PermissionDenied -> ConnectionFailureKind.PERMISSION_DENIED
    SessionFailure.ServerUnreachable,
    SessionFailure.NetworkUnavailable,
    SessionFailure.TransportUnavailable -> ConnectionFailureKind.UNREACHABLE
    SessionFailure.IncompatibleServer -> ConnectionFailureKind.INCOMPATIBLE_SERVER
    SessionFailure.NoChannels -> ConnectionFailureKind.ZERO_CHANNELS
    is SessionFailure.SynchronizationFailed -> when (failure) {
        SessionOperationFailure.ACCESS_DENIED -> ConnectionFailureKind.PERMISSION_DENIED
        SessionOperationFailure.TRANSPORT_UNAVAILABLE,
        SessionOperationFailure.TIMEOUT,
        SessionOperationFailure.CONNECTION_LIMIT -> ConnectionFailureKind.UNREACHABLE
        SessionOperationFailure.SERVER_REJECTED,
        SessionOperationFailure.NOT_SUPPORTED -> ConnectionFailureKind.INCOMPATIBLE_SERVER
    }
    SessionFailure.UnexpectedFailure -> ConnectionFailureKind.OTHER
}

private fun connectionState(state: SessionState): ConnectionState = when (state) {
    SessionState.Disconnected -> ConnectionState.Disconnected
    SessionState.Connecting, SessionState.Synchronizing -> ConnectionState.Connecting
    is SessionState.Ready -> ConnectionState.Connected
    is SessionState.Unavailable -> ConnectionState.Error(state.reason.toFailureKind())
}

private fun <T> DvrMutationResult<T>.toActionResult(): DvrActionResult = when (this) {
    is DvrMutationResult.Confirmed -> DvrActionResult.Accepted((value as? DvrEntryId)?.value?.let(::toIntExact))
    is DvrMutationResult.AcceptedButUnconfirmed -> DvrActionResult.Accepted((value as? DvrEntryId)?.value?.let(::toIntExact))
    DvrMutationResult.AccessDenied -> DvrActionResult.Failed(DvrActionFailure.PERMISSION_DENIED)
    DvrMutationResult.ConnectionLimit -> DvrActionResult.Failed(DvrActionFailure.CONNECTION_LIMIT)
    DvrMutationResult.NotReady,
    DvrMutationResult.Timeout,
    DvrMutationResult.TransportUnavailable -> DvrActionResult.Failed(DvrActionFailure.CONNECTION)
    DvrMutationResult.ServerRejected,
    DvrMutationResult.NotSupported -> DvrActionResult.Failed(DvrActionFailure.REJECTED)
}

private fun DvrProgressResult.toUpdateResult(): RecordingProgressUpdateResult = when (this) {
    DvrProgressResult.Accepted -> RecordingProgressUpdateResult.Accepted
    DvrProgressResult.AccessDenied -> RecordingProgressUpdateResult.PermissionDenied
    DvrProgressResult.NotSupported -> RecordingProgressUpdateResult.Unsupported
    DvrProgressResult.Timeout -> RecordingProgressUpdateResult.Timeout
    DvrProgressResult.NotReady,
    DvrProgressResult.TransportUnavailable -> RecordingProgressUpdateResult.Disconnected
    DvrProgressResult.ServerRejected,
    DvrProgressResult.ConnectionLimit -> RecordingProgressUpdateResult.Rejected
}
