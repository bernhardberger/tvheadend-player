@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopResult
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics

internal fun observedLivePlayIntent(
    activeTarget: AppPlaybackTarget?,
    serverPaused: Boolean?,
): Boolean? = if (activeTarget is AppPlaybackTarget.Live) serverPaused?.not() else null

internal fun liveDiagnosticsForTarget(
    activeTarget: AppPlaybackTarget?,
    diagnostics: LiveSubscriptionDiagnostics?,
): LiveSubscriptionDiagnostics? = diagnostics.takeIf { activeTarget is AppPlaybackTarget.Live }

internal fun activePlayerTargetIsHealthy(
    playerErrorPresent: Boolean,
    playbackState: Int,
): Boolean =
    !playerErrorPresent &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED

internal fun playerReportedPlaybackState(
    currentState: AppPlaybackState,
    recoveryAttemptInProgress: Boolean,
    playbackState: Int,
    isPlaying: Boolean,
): AppPlaybackState = when {
    recoveryAttemptInProgress -> currentState
    playbackState == Player.STATE_ENDED -> AppPlaybackState.Finished
    // Playing describes a ready target; Media3's play intent owns pause/progression.
    isPlaying || playbackState == Player.STATE_READY -> AppPlaybackState.Playing
    // A stall after presentation keeps the watched channel; only a first start is a tune.
    playbackState == Player.STATE_BUFFERING && currentState.presented -> AppPlaybackState.Buffering
    playbackState == Player.STATE_BUFFERING -> AppPlaybackState.Starting
    playbackState == Player.STATE_IDLE -> AppPlaybackState.Idle
    else -> currentState
}

internal fun playerStateAfterRecoveryResolution(
    currentState: AppPlaybackState,
    playbackState: Int,
    isPlaying: Boolean,
): AppPlaybackState = playerReportedPlaybackState(
        currentState = currentState,
        recoveryAttemptInProgress = false,
        playbackState = playbackState,
        isPlaying = isPlaying,
    )

/**
 * Failure published after recovery gave up. The issue is the one the retired live target last
 * reported, carried by [PlaybackStopResult.Stopped] or, when player cleanup failed after the
 * target was retired, by [PlaybackStopResult.PlayerUnavailable]; results that retired nothing
 * report no issue.
 */
internal fun recoveryExhaustedState(
    stopResult: PlaybackStopResult,
    recoveryReason: PlaybackRecoveryReason,
): AppPlaybackState.Failed = AppPlaybackState.Failed(
    reason = AppPlaybackFailureReason.OTHER,
    subscriptionIssue = when (stopResult) {
        is PlaybackStopResult.Stopped -> stopResult.finalSubscriptionIssue
        is PlaybackStopResult.PlayerUnavailable -> stopResult.finalSubscriptionIssue
        PlaybackStopResult.AlreadyStopped,
        PlaybackStopResult.NotRunning,
        PlaybackStopResult.ShutDown,
        -> null
    },
    recoveryReason = recoveryReason,
)
