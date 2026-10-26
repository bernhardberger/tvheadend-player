package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.LocalPlayerEntryHandoff
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import kotlinx.coroutines.delay

internal val LocalWatchingChannel = compositionLocalOf<ChannelId?> { null }
internal val LocalRecordingPlaybackSelection = compositionLocalOf<RecordingPlaybackSelection?> { null }
internal val LocalWatchingTarget = compositionLocalOf<AppPlaybackTarget?> { null }
internal val LocalWatchingIndicator = compositionLocalOf { ChannelPlaybackIndicator.NONE }
internal val LocalRecordingPosition = compositionLocalOf<State<RecordingViewerPosition?>?> { null }
internal val LocalLivePosition = compositionLocalOf<State<LiveViewerPosition?>?> { null }
/** Returns the watched channel to live and plays; absent where no player runtime is provided. */
internal val LocalWatchingGoLive = staticCompositionLocalOf<(suspend () -> Unit)?> { null }

/**
 * A sample with no epoch is time-shifted but unlocated; no sample means live-edge fallback.
 * [behindLive]: paused, or further behind live than the player's live-edge tolerance.
 */
internal data class LiveViewerPosition(val channelId: ChannelId, val epochSec: Long?, val behindLive: Boolean = true)

/** Only the SDK's correlated content-to-clock mapping can locate a time-shifted viewer. */
internal fun liveViewerPosition(channelId: ChannelId, sample: AppTimeshiftState): LiveViewerPosition? {
    if (!sample.available) return null
    val target = sample.playbackTarget
    val mapping = sample.timeline?.wallClockMapping as? TimeshiftWallClockMapping.Estimate
    val epochSec = if (sample.timingKnown && target != null) mapping?.estimate(target)?.epochSeconds else null
    if (epochSec == null && !sample.paused && sample.serverBehindLiveMs?.let { it <= 0L } == true) return null
    return LiveViewerPosition(channelId, epochSec,
        behindLive = sample.paused || !timeshiftPositionPresentation(sample).atLiveEdge)
}

@Composable
internal fun liveViewerPosition(channelId: ChannelId?): LiveViewerPosition? {
    if (channelId == null || LocalWatchingTarget.current != AppPlaybackTarget.Live(channelId) ||
        !LocalWatchingIndicator.current.isPlayingItem) return null
    return LocalLivePosition.current?.value?.takeIf { it.channelId == channelId }
}

/** Whether the viewer of [channelId] is paused or behind live, so going live is a different place. */
@Composable
internal fun watchingBehindLive(channelId: ChannelId?): Boolean = liveViewerPosition(channelId)?.behindLive == true

/** Whether going live from [event]'s watched channel is a different place, and a player runtime can take it there. */
@Composable
internal fun programmeBehindLive(event: EpgEvent): Boolean = LocalWatchingGoLive.current != null &&
    event.channelId != null && event.channelId == LocalWatchingChannel.current && watchingBehindLive(event.channelId)

/**
 * Whether Continue watching returns to [event]: the programme at the viewer's position on the watched
 * channel, as its marks show it, and while behind live also the programme on air there.
 */
@Composable
internal fun programmeWatched(event: EpgEvent, nowSec: Long): Boolean =
    watchingIndicator(event, nowSec) != ChannelPlaybackIndicator.NONE || programmeBehindLive(event) && event.isRunning(nowSec)

/** The watched channel's mark wherever its viewer is, for Continue watching on any of its programmes. */
@Composable
internal fun channelWatchingIndicator(channelId: ChannelId?): ChannelPlaybackIndicator =
    if (channelId != null && channelId == LocalWatchingChannel.current) LocalWatchingIndicator.current
    else ChannelPlaybackIndicator.NONE

/** Until the first sample, or without time-shift, the viewer can only be at live. */
@Composable
internal fun liveViewerEpochSec(channelId: ChannelId?, nowSec: Long): Long? {
    val sample = liveViewerPosition(channelId) ?: return nowSec
    return sample.epochSec
}

internal data class RecordingViewerPosition(val recordingId: DvrEntryId, val positionMs: Long, val durationMs: Long) {
    val fraction: Float? get() = durationMs.takeIf { it > 0 }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
}

@Composable
internal fun watchingIndicator(event: EpgEvent?, nowSec: Long): ChannelPlaybackIndicator {
    if (event == null || event.channelId != LocalWatchingChannel.current) return ChannelPlaybackIndicator.NONE
    val at = liveViewerPosition(event.channelId)?.epochSec ?: nowSec
    return LocalWatchingIndicator.current.takeIf { event.isRunning(at) } ?: ChannelPlaybackIndicator.NONE
}

/** Unknown shifted timing stays unknown; live-edge and ambient progress follow time on air. */
@Composable
internal fun programmeProgress(event: EpgEvent?, nowSec: Long): Float? {
    if (event == null) return null
    val indicator = watchingIndicator(event, nowSec)
    val at = if (indicator.isPlayingItem) liveViewerEpochSec(event.channelId, nowSec) ?: return null else nowSec
    return event.progress(at).takeIf { event.isRunning(at) }
}

@Composable
internal fun watchingIndicator(recordingId: DvrEntryId?): ChannelPlaybackIndicator =
    if (recordingId != null && LocalWatchingTarget.current == AppPlaybackTarget.Recording(recordingId))
        LocalWatchingIndicator.current else ChannelPlaybackIndicator.NONE

@Composable
internal fun recordingViewerPosition(recordingId: DvrEntryId?): RecordingViewerPosition? {
    if (recordingId == null || !watchingIndicator(recordingId).isPlayingItem) return null
    return LocalRecordingPosition.current?.value?.takeIf { it.recordingId == recordingId }
}

/** One identity/intent source; position changes invalidate only consumers of the sampled state. */
@Composable
internal fun ProvideWatchingProgress(runtime: AppPlaybackRuntime, content: @Composable () -> Unit) {
    val target by runtime.activeTarget.collectAsStateWithLifecycle()
    val recordingSelection by runtime.recordingSelection.collectAsStateWithLifecycle()
    val playback by runtime.state.collectAsStateWithLifecycle()
    val playWhenReady by rememberPlaybackIntent(runtime.player)
    val livePause by runtime.livePause.collectAsStateWithLifecycle()
    // A paused seek's brief picture refresh plays the player; the item stays paused.
    val indicator = playbackIndicator(target, target, playback, playWhenReady && !livePause.pausedSeekRefresh)
    val channel = (target as? AppPlaybackTarget.Live)?.channelId
    val position = remember(runtime, target) { mutableStateOf<RecordingViewerPosition?>(null) }
    val livePosition = remember(runtime, target) { mutableStateOf<LiveViewerPosition?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(runtime, target, lifecycle) {
        val sampledTarget = target ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // A suspended live sample must not publish the previous target's coordinates.
                if (runtime.activeTarget.value != sampledTarget) break
                when (sampledTarget) {
                    is AppPlaybackTarget.Recording -> position.value = RecordingViewerPosition(sampledTarget.recordingId,
                        runtime.player.currentPosition.coerceAtLeast(0), runtime.player.duration)
                    is AppPlaybackTarget.Live -> {
                        val sample = runtime.sampleTimeshiftPresentation()
                        if (runtime.activeTarget.value != sampledTarget) break
                        livePosition.value = liveViewerPosition(sampledTarget.channelId, sample)
                    }
                }
                // Sample immediately on a target change or lifecycle restart, then tick each second.
                delay(1_000)
            }
        }
    }
    val goLive: suspend () -> Unit = remember(runtime) { { runtime.goLive(); runtime.play() } }
    CompositionLocalProvider(LocalWatchingChannel provides channel, LocalWatchingTarget provides target,
        LocalWatchingGoLive provides goLive,
        LocalRecordingPlaybackSelection provides recordingSelection,
        LocalWatchingIndicator provides indicator, LocalRecordingPosition provides position,
        LocalLivePosition provides livePosition) {
        // From an activation through its outgoing overlay's fade, browse keeps what it showed when pressed.
        val handoff = LocalPlayerEntryHandoff.current
        HoldWatching(handoff != null && (handoff.pending || handoff.departing), content)
    }
}

private class WatchingPresentation(
    val channel: ChannelId?, val target: AppPlaybackTarget?, val selection: RecordingPlaybackSelection?,
    val indicator: ChannelPlaybackIndicator, val recordingPosition: State<RecordingViewerPosition?>?,
    val livePosition: State<LiveViewerPosition?>?,
)

/**
 * While [held], [content] keeps what was playing when it was last shown, so a page fading into
 * the player doesn't gain the new target's mark or progress mid-fade. Positions are copied when the
 * hold begins: a request can wait while the old target still plays and its sampler still ticks.
 */
@Composable
internal fun HoldWatching(held: Boolean, content: @Composable () -> Unit) {
    val current = WatchingPresentation(LocalWatchingChannel.current, LocalWatchingTarget.current,
        LocalRecordingPlaybackSelection.current, LocalWatchingIndicator.current,
        LocalRecordingPosition.current, LocalLivePosition.current)
    var last by remember { mutableStateOf(current) }
    SideEffect { if (!held) last = current }
    val shown = if (held) last else current
    val recordingPosition = remember(held, shown.recordingPosition) {
        shown.recordingPosition?.takeIf { held }?.let { mutableStateOf(Snapshot.withoutReadObservation { it.value }) }
    } ?: shown.recordingPosition
    val livePosition = remember(held, shown.livePosition) {
        shown.livePosition?.takeIf { held }?.let { mutableStateOf(Snapshot.withoutReadObservation { it.value }) }
    } ?: shown.livePosition
    CompositionLocalProvider(LocalWatchingChannel provides shown.channel, LocalWatchingTarget provides shown.target,
        LocalRecordingPlaybackSelection provides shown.selection, LocalWatchingIndicator provides shown.indicator,
        LocalRecordingPosition provides recordingPosition, LocalLivePosition provides livePosition,
        content = content)
}

/** Single colour decision for all viewer-position and ambient progress strips. */
internal fun progressColor(watching: Boolean, onAir: Color, viewer: Color): Color = if (watching) viewer else onAir

@Composable
internal fun progressColor(watching: Boolean): Color =
    progressColor(watching, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
