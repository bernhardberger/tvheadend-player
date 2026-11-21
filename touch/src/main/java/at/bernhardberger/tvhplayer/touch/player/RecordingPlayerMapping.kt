@file:Suppress("LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.compose.runtime.State
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.RecordingFailureDetail
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.recordingArtwork
import at.bernhardberger.tvhplayer.core.recordingErrorCondition
import at.bernhardberger.tvhplayer.core.recordingFailureDetail
import at.bernhardberger.tvhplayer.core.recordingIsRunning
import at.bernhardberger.tvhplayer.core.recordingMarkStops
import at.bernhardberger.tvhplayer.core.recordingPaddedStop
import at.bernhardberger.tvhplayer.core.recordingProgramMarks
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.channels.channelRow
import at.bernhardberger.tvhplayer.touch.guide.ProgramDetails
import at.bernhardberger.tvhplayer.touch.recordings.RecordingClock
import at.bernhardberger.tvhplayer.touch.recordings.RecordingItem
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsTexts
import at.bernhardberger.tvhplayer.touch.recordings.recordingItem
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Projects one recording route. The caller supplies playback, admission and actions owned by
 * [target], and a null [markerRevision] until its installed selection belongs to this session.
 * [positionMs] is the moving value from the same samples as [timeline].
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvheadendApi::class)
internal fun recordingPlayerState(
    target: AppPlaybackTarget.Recording,
    observation: SessionObservation,
    playback: AppPlaybackState,
    admission: RecordingPlaybackAdmission?,
    timeline: RecordingTimelinePresentation,
    positionMs: State<Long>,
    marks: List<RecordingMark>,
    markerRevision: Long?,
    skipPrompt: RecordingSkipAction?,
    controlsSkip: RecordingSkipAction?,
    adBreakSetting: AdBreakMode,
    adBreakPick: AdBreakMode?,
    playWhenReady: Boolean,
    now: Instant,
    recordedDate: (Instant) -> String,
    finishClock: (Instant) -> String,
    episodeLine: (DvrEntry) -> String?,
    noChannel: String,
    unknownTitle: String,
    tracks: PlayerTracks,
    options: PlaybackOptions,
    menuControls: Set<PlayerControl>,
    brightness: Float,
    allowPictureInPicture: Boolean,
    artwork: ArtworkModels? = null,
    failure: RecordingFailureDetail? = null,
    info: RecordingPlayerInfo? = null,
): RecordingPlayerState {
    val entry = observation.dvrEntry(target.recordingId)
    val recordingInfo = info?.takeIf { entry?.id?.value?.toString() == it.item.id }
    val status = recordingStatus(entry, admission, playback, failure)
    val presented = status == LivePlayerStatus.Playing
    val range = (timeline as? RecordingTimelinePresentation.Seekable)?.range.takeIf { presented }
    val durationMs = range?.endMs ?: 0L
    val revision = markerRevision.takeIf { range != null }
    val programMarks = entry?.takeIf { revision != null }
        ?.let { recordingProgramMarks(it, durationMs) }.orEmpty()
    val displayedMarks = if (revision == null) emptyList() else marks + programMarks
    val stops = recordingMarkStops(displayedMarks, durationMs)
    val hasMarkers = stops.isNotEmpty()
    return RecordingPlayerState(
        key = target.recordingId.value.toString(),
        channel = recordingChannel(entry, observation, now, artwork, noChannel),
        title = entry?.title?.takeIf(String::isNotBlank) ?: unknownTitle,
        episode = entry?.let(episodeLine),
        artwork = null,
        artworkModel = entry?.let { artwork?.picture(recordingArtwork(it), observation) },
        recordedOn = entry?.start?.let(recordedDate).orEmpty(),
        endsAt = entry?.takeIf(::recordingIsRunning)?.let(::recordingPaddedStop)?.let(finishClock),
        frame = null,
        durationMs = durationMs,
        displayDurationMs = range?.displayEndMs ?: durationMs,
        positionMs = positionMs,
        marks = displayedMarks,
        programMarks = programMarks,
        markerRevision = revision,
        adBreakSetting = adBreakSetting,
        adBreakPick = adBreakPick,
        skipPrompt = skipPrompt.takeIf { hasMarkers },
        controlsSkip = controlsSkip.takeIf { hasMarkers },
        status = status,
        paused = presented && !playWhenReady,
        buffering = playback is AppPlaybackState.Buffering && presented,
        failureDetail = playback.recordingFailureDetail(failure).takeIf { status is LivePlayerStatus.Unavailable },
        growing = admission is RecordingPlaybackAdmission.Growing,
        brightness = brightness,
        tracks = tracks,
        options = options,
        offered = recordingControls(presented, recordingInfo != null, hasMarkers, allowPictureInPicture) + menuControls,
        hasAdBreakOptions = displayedMarks.any {
            it.kind == RecordingMarkKind.AD_BREAK && it.startMs in 0 until durationMs
        },
        info = recordingInfo,
    )
}

internal fun ownsRecordingPlayback(
    target: AppPlaybackTarget.Recording,
    active: AppPlaybackTarget?,
    selection: RecordingPlaybackSelection?,
    observation: SessionObservation,
): Boolean = active == target && selection?.recordingId == target.recordingId &&
    selection.currentSession === observation.currentSession

/** The same single-entry projection as Browse; EPG enrichment is supplied by the caller. */
internal fun recordingPlayerInfo(
    entry: DvrEntry,
    admission: RecordingPlaybackAdmission?,
    clock: RecordingClock,
    texts: RecordingsTexts,
    program: ProgramDetails? = null,
    airingDetails: Map<String, ProgramDetails> = emptyMap(),
): RecordingPlayerInfo = RecordingPlayerInfo(
    recordingItem(entry, admission, entry.channelName, texts, clock.now.epochSecond),
    clock, program, airingDetails,
)

@OptIn(UnstableApi::class)
private fun recordingStatus(
    entry: DvrEntry?,
    admission: RecordingPlaybackAdmission?,
    playback: AppPlaybackState,
    failure: RecordingFailureDetail?,
): LivePlayerStatus {
    val condition: AppErrorCondition? = if (admission != null || playback is AppPlaybackState.Failed) {
        recordingErrorCondition(entry, admission, playback as? AppPlaybackState.Failed, failure)
    } else null
    return when {
        playback is AppPlaybackState.Recovering ->
            LivePlayerStatus.Reconnecting(playback.retryDelayMillis.milliseconds.inWholeSeconds.toInt())
        condition != null -> LivePlayerStatus.Unavailable(condition)
        playback.presented -> LivePlayerStatus.Playing
        else -> LivePlayerStatus.Tuning
    }
}

private fun recordingChannel(
    entry: DvrEntry?,
    observation: SessionObservation,
    now: Instant,
    artwork: ArtworkModels?,
    noChannel: String,
): ChannelRow {
    val id = entry?.channelId
    val channel = observation.channelCatalogForDisplay?.channels?.firstOrNull { it.id == id }
    return channel?.let { channelRow(it, observation, now, artwork = artwork) } ?: ChannelRow(
        id = id?.value?.toString().orEmpty(),
        name = entry?.channelName?.takeIf(String::isNotBlank) ?: noChannel,
    )
}

private fun recordingControls(
    presented: Boolean,
    hasInfo: Boolean,
    ownsMarkers: Boolean,
    allowPictureInPicture: Boolean,
): Set<PlayerControl> = buildSet {
    if (hasInfo) add(PlayerControl.Info)
    if (presented) {
        add(PlayerControl.Timeshift)
        if (ownsMarkers) add(PlayerControl.Markers)
        if (allowPictureInPicture) add(PlayerControl.PictureInPicture)
    }
}
