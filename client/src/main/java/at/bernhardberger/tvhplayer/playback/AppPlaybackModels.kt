@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue

sealed interface AppPlaybackState {
    data object Idle : AppPlaybackState
    data object Starting : AppPlaybackState
    data object Playing : AppPlaybackState

    /**
     * A target that has already presented is waiting for more media. This is a stall of the
     * watched content, not a new tune, so surfaces keep the playing presentation.
     */
    data object Buffering : AppPlaybackState
    data object Finished : AppPlaybackState

    /** The target has been presented and any Media3 buffering is a stall rather than a tune. */
    val presented: Boolean
        get() = this is Playing || this is Buffering
    data class Recovering(
        val reason: PlaybackRecoveryReason,
        val retryDelayMillis: Long,
    ) : AppPlaybackState
    data class Failed(
        val reason: AppPlaybackFailureReason,
        val targetResult: PlaybackTargetResult? = null,
        /** Media3 error code name for a player-reported failure; diagnostics only, never a message. */
        val playerErrorCode: String? = null,
        /** Last TVHeadend subscription issue seen before the target was retired, so the user sees why. */
        val subscriptionIssue: SubscriptionIssue? = null,
        /** Last SDK recovery reason that led to retiring the target. */
        val recoveryReason: PlaybackRecoveryReason? = null,
    ) : AppPlaybackState
}

enum class AppPlaybackFailureReason { RECORDING_READ_FAILED, OTHER }
sealed interface AppPlaybackTarget {
    data class Live(val channelId: ChannelId) : AppPlaybackTarget
    data class Recording(val recordingId: DvrEntryId) : AppPlaybackTarget
}
data class LivePlaybackSelection(
    val currentSession: CurrentSessionObservation,
    val channelId: ChannelId,
)
data class RecordingPlaybackSelection(
    val currentSession: CurrentSessionObservation,
    val recordingId: DvrEntryId,
)

fun currentLivePlaybackSelection(
    observation: SessionObservation,
    channelId: ChannelId,
): LivePlaybackSelection? {
    val currentSession = observation.currentSession ?: return null
    if (observation.channel(channelId) == null) return null
    return LivePlaybackSelection(currentSession, channelId)
}

fun resolveLivePlaybackSelection(
    observation: SessionObservation,
    channelId: ChannelId,
    requestedSelection: LivePlaybackSelection?,
): LivePlaybackSelection? {
    val current = currentLivePlaybackSelection(observation, channelId) ?: return null
    return requestedSelection?.takeIf { requested ->
        requested.channelId == channelId &&
            requested.currentSession === current.currentSession
    } ?: current
}

fun currentRecordingPlaybackSelection(
    observation: SessionObservation,
    recordingId: DvrEntryId,
): RecordingPlaybackSelection? {
    val currentSession = observation.currentSession ?: return null
    if (observation.dvrEntry(recordingId) == null) return null
    return RecordingPlaybackSelection(currentSession, recordingId)
}

internal fun recordingRouteNeedsRestoration(
    routeSelection: RecordingPlaybackSelection,
    activeTarget: AppPlaybackTarget?,
    selectedRecording: RecordingPlaybackSelection?,
): Boolean =
    activeTarget != AppPlaybackTarget.Recording(routeSelection.recordingId) ||
        selectedRecording?.let { selected ->
            selected.recordingId == routeSelection.recordingId &&
                selected.currentSession === routeSelection.currentSession
        } != true

/** What the live Pause control can do for the current live target. */
enum class LivePauseAvailability {
    /** No live target: recordings and idle keep their own controls. */
    NONE,
    /** Timeshift was requested and the grant is not decided yet. A press waits for it. */
    STARTING,
    /** The SDK reports timeshift available for the current target. */
    READY,
    /** The target reached playback without a timeshift grant. */
    UNAVAILABLE,
    /** Timeshift was not requested for this target. */
    OFF,
}

/** [pending] is a Pause pressed before the first picture: local only, the server pause waits for it. */
data class LivePauseState(
    val availability: LivePauseAvailability = LivePauseAvailability.NONE,
    val pending: Boolean = false,
)

/** One-shot: a pending pause was dropped because the channel has no timeshift. Identity is the event. */
class LivePauseUnavailableNotice internal constructor()

data class AppTimeshiftState(
    val available: Boolean = false,
    val paused: Boolean = false,
    val bufferStartMs: Long = 0L,
    val positionMs: Long = 0L,
    val liveEdgeMs: Long = 0L,
    /**
     * Server reader shift behind the live edge, when reported. This is the timeshift
     * position TVHeadend serves; [positionMs] against [liveEdgeMs] additionally contains
     * the client's delivery and decode latency, which is not timeshift.
     */
    val serverBehindLiveMs: Long? = null,
    val capacityMs: Long? = null,
    val timingKnown: Boolean = available,
    val timeline: at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline? = null,
    val playbackTarget: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget? = null,
    val playbackSeek: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekToken? = null,
    /** UI-only extrapolated edge. Never used to create or authorize a seek target. */
    val displayLiveEdgeMs: Long? = null,
    /** Retained SDK mapping for stable historical-boundary display within one segment. */
    val historyStartTimeline: at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline? = null,
)

data class TimeshiftSeekDecision(
    val targetMs: Long,
    val deltaMs: Long,
    val clamped: Boolean,
)
enum class AppPlaybackSource { NONE, LIVE_TV, RECORDING }
data class AppPlaybackFormatDiagnostics(
    val codec: String?,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRateHz: Int? = null,
)
data class AppPlaybackDiagnostics(
    val source: AppPlaybackSource = AppPlaybackSource.NONE,
    val state: AppPlaybackState = AppPlaybackState.Idle,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val bufferedMs: Long = 0L,
    val video: AppPlaybackFormatDiagnostics? = null,
    val audio: AppPlaybackFormatDiagnostics? = null,
    val live: LiveSubscriptionDiagnostics? = null,
)

data class AppVideoPresentation(
    val epoch: Long = 0L,
    val visible: Boolean = false,
)

internal fun AppVideoPresentation.beginTarget(epoch: Long) =
    AppVideoPresentation(epoch = epoch)

internal fun AppVideoPresentation.onFirstFrame(
    frameEpoch: Long,
    activeTargetEpoch: Long?,
): AppVideoPresentation = if (
    epoch == frameEpoch && activeTargetEpoch == frameEpoch
) {
    copy(visible = true)
} else {
    this
}

enum class BackgroundPlaybackNotice { LIMIT_EXPIRED, TUNER_LOST }
