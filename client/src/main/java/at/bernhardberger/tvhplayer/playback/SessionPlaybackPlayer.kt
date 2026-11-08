@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import android.os.Bundle
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelMediaKind
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.mediaKind
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import androidx.media3.common.SimpleBasePlayer
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvhplayer.core.committedProgrammeEvent
import at.bernhardberger.tvhplayer.core.nextProgrammeEvent
import at.bernhardberger.tvhplayer.core.livePlayWhenReady
import at.bernhardberger.tvhplayer.core.programmePositionWindow
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** [pausable]: live timeshift is granted, or live pause still awaits the grant or first picture. */
internal fun sessionPlaybackCommands(target: AppPlaybackTarget?, pausable: Boolean, seekable: Boolean): Player.Commands {
    if (target == null) return Player.Commands.EMPTY
    return Player.Commands.Builder().addAll(
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_STOP,
    ).apply {
        if (target is AppPlaybackTarget.Recording || pausable) add(Player.COMMAND_PLAY_PAUSE)
        if (target is AppPlaybackTarget.Recording && seekable) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    }.build()
}

/** App-owned localized presentation; the reusable client never depends on app resources. */
interface SessionMetadataLabels {
    fun genre(genre: ContentGenre): String?
    fun channelNumber(channel: Channel): String?
    fun error(kind: SessionPlaybackErrorKind): String
}

/** Encoded, bounded artwork only. Null means no source; failures throw and are not cached. */
fun interface SessionArtworkLoader {
    suspend fun load(request: SessionArtworkRequest, observation: SessionObservation): ByteArray?
}

data class SessionArtworkRequest(
    val target: AppPlaybackTarget,
    val channelIcon: ChannelIcon?,
    val recordingImage: ArtworkId?,
    val session: CurrentSessionObservation,
)

enum class SessionPlaybackErrorKind { RECORDING, CHANNEL_UNAVAILABLE, CHANNEL }

private const val SESSION_EXTRA_PREFIX = "at.bernhardberger.tvhplayer.session."
private val SESSION_WHITESPACE = Regex("[\\s\\p{Z}]+")

private fun String?.sessionText(limit: Int = 256): String? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return value.substring(0, value.offsetByCodePoints(0, minOf(limit, value.codePointCount(0, value.length)))).trimEnd()
}

/** MediaMetadata.equals deliberately excludes extras, so compare their values separately. */
internal data class SessionMetadataSnapshot(val metadata: MediaMetadata, val extras: Map<String, Any>)

internal fun sessionPlaybackMetadata(
    target: AppPlaybackTarget?, observation: SessionObservation, now: Instant,
    timeshiftState: AppTimeshiftState,
    liveStart: Boolean,
    programmeWindowEnabled: Boolean,
    labels: SessionMetadataLabels,
): MediaMetadata = sessionMetadataSnapshot(target, observation, now, timeshiftState, liveStart,
    programmeWindowEnabled, labels).metadata

private fun sessionMetadataSnapshot(
    target: AppPlaybackTarget?, observation: SessionObservation, now: Instant,
    timeshiftState: AppTimeshiftState, liveStart: Boolean, programmeWindowEnabled: Boolean,
    labels: SessionMetadataLabels,
): SessionMetadataSnapshot {
    if (target == null) return SessionMetadataSnapshot(MediaMetadata.EMPTY, emptyMap())
    val live = target is AppPlaybackTarget.Live
    val recording = (target as? AppPlaybackTarget.Recording)?.let { observation.dvrEntry(it.recordingId) }
    val channel = when (target) {
        is AppPlaybackTarget.Live -> observation.channel(target.channelId)
        is AppPlaybackTarget.Recording -> recording?.channelId?.let(observation::channel)
    }
    val window = (target as? AppPlaybackTarget.Live)?.takeIf { programmeWindowEnabled }?.let {
        programmePositionWindow(timeshiftState) { at -> observation.eventAt(it.channelId, at) }
    }
    val programme = (target as? AppPlaybackTarget.Live)?.let {
        committedProgrammeEvent(window != null, window?.event, timeshiftState,
            observation.eventAt(it.channelId, now), liveStart && !timeshiftState.paused)
    }
    val next = (target as? AppPlaybackTarget.Live)?.let {
        nextProgrammeEvent(window?.estimatedPosition, window?.event, false, timeshiftState,
            observation.nextEvent(it.channelId, now), liveStart && !timeshiftState.paused, programme) { at ->
            observation.nextEvent(it.channelId, at)
        }
    }
    val channelName = (channel?.name.sessionText() ?: recording?.channelName.sessionText())
    val title = if (live) channelName else recording?.title.sessionText()
    val programmeTitle = (if (live) programme?.title else recording?.title).sessionText()
    val episodeSubtitle = (if (live) programme?.subtitle else recording?.subtitle).sessionText()
    val episode = if (live) programme?.episode else recording?.episode
    val start = if (live) programme?.start else recording?.start
    val stop = if (live) programme?.stop else recording?.stop
    val contentGenre = if (live) programme?.contentGenre else recording?.contentGenre
    val genre = (programme?.genre.sessionText() ?: contentGenre?.let(labels::genre).sessionText())
    val summary = if (live) programme?.summary else recording?.summary
    val description = if (live) programme?.description else recording?.description
    val shortDescription = (summary?.takeIf { it.isNotBlank() } ?: description)
        ?.replace(SESSION_WHITESPACE, " ")?.trim().sessionText(1_024)
    val mediaKind = channel?.mediaKind ?: ChannelMediaKind.UNKNOWN
    val kind = when (mediaKind) {
        ChannelMediaKind.TV -> "tv"
        ChannelMediaKind.RADIO -> "radio"
        ChannelMediaKind.OTHER -> "other"
        else -> "unknown"
    }
    val mediaType = when {
        mediaKind == ChannelMediaKind.TV -> if (live) MediaMetadata.MEDIA_TYPE_TV_CHANNEL else MediaMetadata.MEDIA_TYPE_TV_SHOW
        live && mediaKind == ChannelMediaKind.RADIO -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
        else -> null
    }
    val extras = buildMap<String, Any> {
        fun text(key: String, value: String?) { value?.let { put(SESSION_EXTRA_PREFIX + key, it) } }
        fun number(key: String, value: Long?) { value?.let { put(SESSION_EXTRA_PREFIX + key, it) } }
        number("schemaVersion", 1L)
        text("contentKind", if (live) "live" else "recording")
        text("channelMediaKind", kind)
        text("channelName", channelName)
        text("channelNumber", channel?.let(labels::channelNumber).sessionText())
        text("programmeTitle", programmeTitle)
        text("episodeSubtitle", episodeSubtitle)
        text("episodeOnscreen", episode?.onscreen.sessionText())
        number("seasonNumber", episode?.seasonNumber)
        number("seasonCount", episode?.seasonCount)
        number("episodeNumber", episode?.episodeNumber)
        number("episodeCount", episode?.episodeCount)
        number("partNumber", episode?.partNumber)
        number("partCount", episode?.partCount)
        if (start != null && stop != null && start < stop) {
            number("programmeStartEpochMs", start.toEpochMilliseconds())
            number("programmeStopEpochMs", stop.toEpochMilliseconds())
            number("programmeDurationMs", (stop - start).inWholeMilliseconds)
        }
        text("genre", genre)
        genre?.let { put("android.media.metadata.GENRE", it) }
        number("contentGenreCode", contentGenre?.let { (it.subgenre?.code ?: (it.category.code shl 4)).toLong() })
        text("shortDescription", shortDescription)
        text("nextProgrammeTitle", next?.title.sessionText())
        if (next != null && next.start < next.stop) {
            number("nextProgrammeStartEpochMs", next.start.toEpochMilliseconds())
            number("nextProgrammeStopEpochMs", next.stop.toEpochMilliseconds())
        }
    }
    val bundle = Bundle().apply {
        extras.forEach { (key, value) -> when (value) {
            is String -> putString(key, value)
            is Long -> putLong(key, value)
        } }
    }
    return SessionMetadataSnapshot(MediaMetadata.Builder()
        .setTitle(title).setDisplayTitle(title)
        .setSubtitle(if (live) programmeTitle else episodeSubtitle ?: channelName)
        .setArtist(if (live) programmeTitle else channelName).setStation(channelName).setGenre(genre).setMediaType(mediaType)
        .setExtras(bundle).build(), extras)
}

/** Read-only presentation plus narrowly admitted runtime commands. Never owns the raw player. */
class SessionPlaybackPlayer(
    private val runtime: AppPlaybackRuntime,
    private val programmeWindowEnabled: Boolean,
    private val labels: SessionMetadataLabels,
    private val artworkLoader: SessionArtworkLoader = SessionArtworkLoader { _, _ -> null },
) : ForwardingSimpleBasePlayer(runtime.player) {
    private var metadata = SessionMetadataSnapshot(MediaMetadata.EMPTY, emptyMap())
    private var metadataTarget: AppPlaybackTarget? = null
    private var itemUid = Any()
    private var closed = false
    private var errorKey: SessionFailureKey? = null
    private var error: PlaybackException? = null
    private var artworkRequest: SessionArtworkRequest? = null
    private var artworkData: ByteArray? = null
    private var artworkJob: Job? = null

    /** The activity starts this on the player's looper and cancels it before releasing the session. */
    suspend fun observe(observation: StateFlow<SessionObservation>) = coroutineScope {
        // Small, session-local cache of successes and source absence, never failed loads.
        val cache = java.util.LinkedHashMap<SessionArtworkRequest, ByteArray?>(8, 0.75f, true)
        var cacheSession: CurrentSessionObservation? = null
        val clock = flow {
            while (true) {
                emit(Clock.System.now())
                delay(1_000)
            }
        }
        val sampledClock = combine(runtime.activeTarget, clock) { target, now ->
            val sample = if (target is AppPlaybackTarget.Live) runtime.sampleTimeshiftPresentation() else AppTimeshiftState()
            Triple(target, sample.takeIf { runtime.activeTarget.value == target } ?: AppTimeshiftState(), now)
        }
        val livePresentation = combine(runtime.livePlaybackObservation, runtime.liveStartUndisturbed, runtime.state,
            runtime.recordingAdmission) { live, start, state, admission ->
            SessionRuntimePresentation(live, start, state, admission is RecordingPlaybackAdmission.Growing)
        }
        combine(runtime.activeTarget, livePresentation, runtime.livePause, observation, sampledClock) {
                target, live, livePause, snapshot, sampled ->
            val timeshift = sampled.second.takeIf { sampled.first == target } ?: AppTimeshiftState()
            val liveStart = live.liveStart && livePlayWhenReady(livePause, runtime.player.playWhenReady)
            SessionPresentationSnapshot(target, livePause,
                (live.live as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available,
                live.state, live.growing,
                sessionMetadataSnapshot(target, snapshot, sampled.third, timeshift, liveStart, programmeWindowEnabled, labels),
                sessionArtworkRequest(target, snapshot))
        }.distinctUntilChanged().collect { value ->
            if (!closed) {
                val target = value.target
                if (value.artwork != artworkRequest && target == runtime.activeTarget.value) {
                    artworkJob?.cancel()
                    artworkRequest = value.artwork
                    artworkData = null
                    val request = value.artwork
                    if (request != null) {
                        if (request.session != cacheSession) {
                            cache.clear()
                            cacheSession = request.session
                        }
                        if (cache.containsKey(request)) artworkData = cache[request]
                        else {
                            val captured = observation.value
                            artworkJob = launch {
                                val bytes = try {
                                    withContext(Dispatchers.IO) { artworkLoader.load(request, captured) }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    return@launch
                                }
                                if (!closed && artworkRequest == request &&
                                    sessionArtworkRequest(runtime.activeTarget.value, observation.value) == request) {
                                    if (bytes != null || (request.channelIcon == null && request.recordingImage == null)) cache[request] = bytes
                                    if (cache.size > 8) cache.remove(cache.keys.first())
                                    artworkData = bytes
                                    invalidateState()
                                }
                            }
                        }
                    }
                }
                if (target != null && target == runtime.activeTarget.value) {
                    if (metadataTarget != target) itemUid = Any()
                    metadataTarget = target
                    metadata = value.metadata
                } else if (runtime.state.value == AppPlaybackState.Idle) {
                    metadataTarget = null
                    metadata = SessionMetadataSnapshot(MediaMetadata.EMPTY, emptyMap())
                }
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        if (closed) return State.Builder().build()
        val activeTarget = runtime.activeTarget.value
        val state = runtime.state.value
        if (activeTarget == null && state == AppPlaybackState.Idle) {
            metadataTarget = null
            metadata = SessionMetadataSnapshot(MediaMetadata.EMPTY, emptyMap())
        }
        val failure = state as? AppPlaybackState.Failed
        val target = activeTarget ?: failure?.target
        val nextErrorKey = failure?.sessionFailureKey()
        if (nextErrorKey != errorKey) {
            errorKey = nextErrorKey
            error = nextErrorKey?.let { PlaybackException(labels.error(it.kind), null, it.code) }
        }
        val failedCommands = if (failure != null) Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_STOP).build() else null
        if (target == null || (failure != null && (metadataTarget != failure.target || target != failure.target))) return State.Builder()
            .setPlayerError(error).setAvailableCommands(failedCommands ?: Player.Commands.EMPTY).build()
        val timeshift = (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState is LiveTimeshiftState.Available
        val livePause = runtime.livePause.value
        val seekable = target is AppPlaybackTarget.Recording && player.isCurrentMediaItemSeekable &&
            player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        // Publish a sanitized single-item timeline, never the SDK's URI, IDs, extras or raw errors.
        val currentMetadata = (metadata.metadata.takeIf { metadataTarget == target } ?: MediaMetadata.EMPTY)
            .buildUpon().setArtworkData(artworkData.takeIf { artworkRequest?.target == target }, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
        val item = MediaItemData.Builder(itemUid)
            .setMediaItem(MediaItem.Builder()
                .apply { if (target is AppPlaybackTarget.Live) setLiveConfiguration(MediaItem.LiveConfiguration.UNSET) }
                .build())
            .setMediaMetadata(currentMetadata)
            .setLiveConfiguration(if (target is AppPlaybackTarget.Live) MediaItem.LiveConfiguration.UNSET else null)
            .setIsDynamic(target is AppPlaybackTarget.Live || runtime.recordingAdmission.value is RecordingPlaybackAdmission.Growing)
            .setIsSeekable(seekable && failure == null)
            .setDurationUs(if (target is AppPlaybackTarget.Live || player.duration == C.TIME_UNSET) C.TIME_UNSET else player.duration * 1_000)
            .build()
        return super.getState().buildUpon()
            .setAvailableCommands(failedCommands ?: sessionPlaybackCommands(target, timeshift || livePause.availability.acceptsPause(), seekable))
            .apply {
                // A pending channel change: Play/Pause address that channel, not the installed player.
                if (livePause.selectionPending) {
                    setPlayWhenReady(!livePause.pending, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                } else if (livePause.pausedSeekRefresh) {
                    // A paused seek plays briefly to show its picture: controllers still see Pause.
                    setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                }
            }
            .setPlaylist(listOf(item))
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .setCurrentMediaItemIndex(0)
            .setPlayerError(error)
            .apply {
                if (failure != null) {
                    setPlaybackState(Player.STATE_IDLE)
                    setIsLoading(false)
                    setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                }
            }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        awaitCommand(runtime.setSessionPlayWhenReady(playWhenReady) { !closed })

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        awaitCommand(runtime.seekRecordingFromSession(positionMs) { !closed })

    /** The player's own Stop: runtime teardown, then a showing player screen closes. */
    override fun handleStop(): ListenableFuture<*> =
        awaitCommand(runtime.stopFromSession { !closed })

    private fun awaitCommand(job: Job): ListenableFuture<*> {
        val result = SettableFuture.create<Void>()
        job.invokeOnCompletion { failure ->
            if (failure == null) result.set(null) else result.cancel(false)
        }
        return result
    }

    /** Release is local lifecycle ownership, never an advertised controller command. */
    fun close() {
        if (closed) return
        closed = true
        artworkJob?.cancel()
        artworkData = null
        // Commands belong to the runtime: let their local/server transaction finish even
        // after this presentation wrapper is detached. Their futures may still complete.
        // ForwardingSimpleBasePlayer owns a private listener. Swapping detaches it without
        // releasing the app-scoped ExoPlayer (which must survive activity stop/start).
        setPlayer(object : SimpleBasePlayer(applicationLooper) {
            override fun getState(): State = State.Builder().build()
        })
    }
}

private data class SessionRuntimePresentation(
    val live: LivePlaybackObservation, val liveStart: Boolean, val state: AppPlaybackState, val growing: Boolean,
)

private data class SessionPresentationSnapshot(
    val target: AppPlaybackTarget?, val pause: LivePauseState, val timeshift: Boolean,
    val state: AppPlaybackState, val growing: Boolean, val metadata: SessionMetadataSnapshot,
    val artwork: SessionArtworkRequest?,
)

private fun sessionArtworkRequest(target: AppPlaybackTarget?, observation: SessionObservation): SessionArtworkRequest? {
    val session = observation.currentSession ?: return null
    if (target == null) return null
    val recording = (target as? AppPlaybackTarget.Recording)?.let { observation.dvrEntry(it.recordingId) }
    val channel = when (target) {
        is AppPlaybackTarget.Live -> observation.channel(target.channelId)
        is AppPlaybackTarget.Recording -> recording?.channelId?.let(observation::channel)
    }
    return SessionArtworkRequest(target, channel?.icon, recording?.image, session)
}

private data class SessionFailureKey(val kind: SessionPlaybackErrorKind, val code: Int, val target: AppPlaybackTarget?)

private fun AppPlaybackState.Failed.sessionFailureKey(): SessionFailureKey {
    val channelUnavailable = subscriptionIssue != null || targetResult == PlaybackTargetResult.TARGET_UNAVAILABLE
    val kind = when {
        target is AppPlaybackTarget.Recording ||
            (target == null && reason == AppPlaybackFailureReason.RECORDING_READ_FAILED) -> SessionPlaybackErrorKind.RECORDING
        channelUnavailable -> SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE
        else -> SessionPlaybackErrorKind.CHANNEL
    }
    val code = when {
        reason == AppPlaybackFailureReason.RECORDING_READ_FAILED -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        channelUnavailable -> PlaybackException.ERROR_CODE_REMOTE_ERROR
        deviceFault -> PlaybackException.ERROR_CODE_DECODING_FAILED
        else -> PlaybackException.ERROR_CODE_REMOTE_ERROR
    }
    return SessionFailureKey(kind, code, target)
}
