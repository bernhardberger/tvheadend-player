package at.bernhardberger.tvhplayer.data

/** UI-facing projection of SDK channel metadata. IDs remain validated by the SDK adapter. */
data class Channel(
    val channelId: Int,
    val name: String,
    val number: Int?,
    val icon: String?,
    val tagIds: Set<Int> = emptySet(),
    val currentEventId: Int? = null,
    val nextEventId: Int? = null,
)

data class ChannelTag(val id: Int, val name: String, val index: Int)

data class EpgEventEntry(
    val eventId: Int,
    val channelId: Int,
    val start: Long,
    val stop: Long,
    val title: String,
    val summary: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val contentType: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeCount: Int? = null,
    val partNumber: Int? = null,
    val partCount: Int? = null,
    val episodeId: Int? = null,
    val seriesLinkId: Int? = null,
    val image: String? = null,
    val dvrId: Int? = null,
)

enum class DvrState { SCHEDULED, RECORDING, COMPLETED, FAILED, CANCELLED, UNKNOWN }

data class DvrFile(val id: Int? = null, val path: String? = null, val size: Long? = null)

data class DvrConfig(
    val id: String,
    val name: String,
    val comment: String? = null,
    val enabled: Boolean = true,
)

data class DvrTimeRecordingRule(
    val id: String,
    val enabled: Boolean,
    val name: String,
    val title: String,
    val channelId: Int,
    val startMinutesSinceMidnight: Int,
    val stopMinutesSinceMidnight: Int,
    val daysOfWeekMask: Long? = null,
    val priority: Long? = null,
    val retentionDays: Long? = null,
    val directory: String? = null,
    val owner: String? = null,
    val creator: String? = null,
    val configId: String? = null,
    val comment: String? = null,
)

data class DvrEntry(
    val id: Int,
    val eventId: Int?,
    val channelId: Int,
    val start: Long,
    val stop: Long,
    val title: String,
    val subtitle: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val state: DvrState,
    val failureReason: String? = null,
    val configId: String? = null,
    val files: List<DvrFile> = emptyList(),
    val owner: String? = null,
    val creator: String? = null,
    val path: String? = null,
    val channelName: String? = null,
    val image: String? = null,
    val fanartImage: String? = null,
    val playPosition: Long? = null,
    val playCount: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeCount: Int? = null,
    val partNumber: Int? = null,
    val partCount: Int? = null,
    val autorecId: String? = null,
    val timerecId: String? = null,
    val dataSizeBytes: Long? = null,
    val enabled: Boolean? = null,
    val contentType: Int? = null,
    val subscriptionError: String? = null,
    val startExtraMinutes: Long? = null,
    val stopExtraMinutes: Long? = null,
)

enum class DvrActionFailure { PERMISSION_DENIED, CONNECTION_LIMIT, CONFLICT, REJECTED, CONNECTION }

sealed interface DvrActionResult {
    data class Accepted(val entryId: Int? = null) : DvrActionResult
    data class Failed(val reason: DvrActionFailure) : DvrActionResult
}

enum class RecordingProgressCapability { Disconnected, Unsupported, ReadOnly, Full }

sealed interface RecordingProgressUpdateResult {
    data object Accepted : RecordingProgressUpdateResult
    data object PermissionDenied : RecordingProgressUpdateResult
    data object Unsupported : RecordingProgressUpdateResult
    data object Timeout : RecordingProgressUpdateResult
    data object Disconnected : RecordingProgressUpdateResult
    data object Rejected : RecordingProgressUpdateResult
}

enum class ConnectionFailureKind {
    AUTHENTICATION,
    DNS,
    UNREACHABLE,
    TIMEOUT,
    INCOMPATIBLE_SERVER,
    PERMISSION_DENIED,
    ZERO_CHANNELS,
    OTHER,
}

enum class SubscriptionFailureKind {
    INVALID_TARGET,
    NO_FREE_ADAPTER,
    MUX_NOT_ENABLED,
    TUNING_FAILED,
    BAD_SIGNAL,
    SCRAMBLED,
    OVERRIDDEN,
    NO_INPUT,
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val kind: ConnectionFailureKind) : ConnectionState
}

sealed interface RecordingPlaybackIntent {
    data object DefaultPolicy : RecordingPlaybackIntent
    data object FromBeginning : RecordingPlaybackIntent
    data class Resume(val positionSeconds: Long) : RecordingPlaybackIntent
}

sealed interface RecordingPlaybackAvailability {
    data class Ready(val path: String, val size: Long?, val growing: Boolean) : RecordingPlaybackAvailability
    data object NotReady : RecordingPlaybackAvailability
    data object FileUnavailable : RecordingPlaybackAvailability
}

fun recordingPlaybackAvailability(entry: DvrEntry): RecordingPlaybackAvailability {
    if (entry.state !in setOf(DvrState.COMPLETED, DvrState.RECORDING, DvrState.FAILED)) {
        return RecordingPlaybackAvailability.NotReady
    }
    val file = entry.files.firstOrNull { !it.path.isNullOrBlank() }
    if (file == null && entry.path.isNullOrBlank()) return RecordingPlaybackAvailability.FileUnavailable
    return RecordingPlaybackAvailability.Ready(
        path = "/dvrfile/${entry.id}",
        size = file?.size,
        growing = entry.state == DvrState.RECORDING,
    )
}

fun recordingResumeCandidateSeconds(state: DvrState, serverPositionSeconds: Long?): Long? =
    serverPositionSeconds?.takeIf { state == DvrState.COMPLETED && it >= 180L }

fun recordingSecondsToMediaMilliseconds(positionSeconds: Long): Long? = when {
    positionSeconds < 0L || positionSeconds > Long.MAX_VALUE / 1_000L -> null
    else -> positionSeconds * 1_000L
}
