@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId

internal sealed interface ForegroundPlaybackAction {
    data object None : ForegroundPlaybackAction
    data object StopLive : ForegroundPlaybackAction
    data object PauseRecording : ForegroundPlaybackAction
    data class ResumeLive(val channelId: ChannelId, val notice: BackgroundPlaybackNotice? = null) : ForegroundPlaybackAction
    data class KeepLive(val epoch: Long, val deadlineMillis: Long) : ForegroundPlaybackAction
    data class ResumeKeptLive(val epoch: Long, val resume: Boolean) : ForegroundPlaybackAction
    data object ResumeRecording : ForegroundPlaybackAction
}

internal suspend fun executeForegroundPlaybackAction(
    action: ForegroundPlaybackAction,
    stopLive: suspend () -> Unit,
    pauseRecording: () -> Unit,
    resumeLive: suspend (ChannelId) -> Unit,
    resumeRecording: suspend () -> Unit,
    keepLive: suspend (ForegroundPlaybackAction.KeepLive) -> Unit = {},
    resumeKeptLive: suspend (ForegroundPlaybackAction.ResumeKeptLive) -> Unit = {},
    notice: (BackgroundPlaybackNotice) -> Unit = {},
) {
    when (action) {
        ForegroundPlaybackAction.None -> Unit
        ForegroundPlaybackAction.StopLive -> stopLive()
        ForegroundPlaybackAction.PauseRecording -> pauseRecording()
        is ForegroundPlaybackAction.ResumeLive -> {
            resumeLive(action.channelId)
            action.notice?.let(notice)
        }
        is ForegroundPlaybackAction.KeepLive -> keepLive(action)
        is ForegroundPlaybackAction.ResumeKeptLive -> resumeKeptLive(action)
        ForegroundPlaybackAction.ResumeRecording -> resumeRecording()
    }
}

private sealed interface BackgroundedPlaybackTarget {
    data class Live(
        val channelId: ChannelId,
        val keptEpoch: Long? = null,
        val deadlineMillis: Long = 0,
        val resume: Boolean = false,
        val notice: BackgroundPlaybackNotice? = null,
    ) : BackgroundedPlaybackTarget
    data class Recording(
        val recordingId: DvrEntryId,
        val targetEpoch: Long,
        val resumeOnForeground: Boolean,
    ) : BackgroundedPlaybackTarget
}

internal class ForegroundPlaybackLifecycle {
    private var foreground = true
    private var backgroundedTarget: BackgroundedPlaybackTarget? = null

    fun onBackgrounded(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        recordingPlayWhenReady: Boolean,
        timeshiftAvailable: Boolean = false,
        serverPaused: Boolean = false,
        keepMinutes: Int = 0,
        interactive: Boolean = true,
        nowMillis: Long = 0,
        recoveryPending: Boolean = false,
    ): ForegroundPlaybackAction {
        if (!foreground) return ForegroundPlaybackAction.None
        foreground = false
        if (activeTarget is AppPlaybackTarget.Live && recoveryPending) {
            backgroundedTarget = BackgroundedPlaybackTarget.Live(activeTarget.channelId, notice = BackgroundPlaybackNotice.TUNER_LOST)
            return ForegroundPlaybackAction.StopLive
        }
        if (activeTarget is AppPlaybackTarget.Live && activeTargetEpoch != null &&
            timeshiftAvailable && keepMinutes > 0 && interactive) {
            val deadline = nowMillis + keepMinutes * 60_000L
            backgroundedTarget = BackgroundedPlaybackTarget.Live(
                activeTarget.channelId, activeTargetEpoch, deadline,
                recordingPlayWhenReady && !serverPaused,
            )
            return ForegroundPlaybackAction.KeepLive(activeTargetEpoch, deadline)
        }
        return rememberBackgroundedTarget(
            activeTarget = activeTarget,
            activeTargetEpoch = activeTargetEpoch,
            recordingPlayWhenReady = recordingPlayWhenReady,
        )
    }

    fun onForegrounded(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        nowMillis: Long = 0,
    ): ForegroundPlaybackAction {
        if (foreground) return ForegroundPlaybackAction.None
        foreground = true
        val target = backgroundedTarget
        backgroundedTarget = null
        return when (target) {
            is BackgroundedPlaybackTarget.Live -> when {
                target.keptEpoch == null -> ForegroundPlaybackAction.ResumeLive(target.channelId, target.notice)
                activeTarget != AppPlaybackTarget.Live(target.channelId) || activeTargetEpoch != target.keptEpoch ->
                    ForegroundPlaybackAction.None
                nowMillis >= target.deadlineMillis -> ForegroundPlaybackAction.ResumeLive(
                    target.channelId, BackgroundPlaybackNotice.LIMIT_EXPIRED,
                )
                else -> ForegroundPlaybackAction.ResumeKeptLive(target.keptEpoch, target.resume)
            }
            is BackgroundedPlaybackTarget.Recording -> if (
                target.resumeOnForeground &&
                activeTarget == AppPlaybackTarget.Recording(target.recordingId) &&
                activeTargetEpoch == target.targetEpoch
            ) {
                ForegroundPlaybackAction.ResumeRecording
            } else {
                ForegroundPlaybackAction.None
            }
            null -> ForegroundPlaybackAction.None
        }
    }

    fun onExplicitStop() {
        backgroundedTarget = null
    }

    fun isKeeping(epoch: Long?): Boolean = !foreground && epoch != null &&
        (backgroundedTarget as? BackgroundedPlaybackTarget.Live)?.keptEpoch == epoch

    fun releaseKept(epoch: Long?, notice: BackgroundPlaybackNotice? = null): ForegroundPlaybackAction {
        if (!isKeeping(epoch)) return ForegroundPlaybackAction.None
        val target = backgroundedTarget as BackgroundedPlaybackTarget.Live
        backgroundedTarget = target.copy(keptEpoch = null, notice = notice)
        return ForegroundPlaybackAction.StopLive
    }

    fun onTargetStarted(
        activeTarget: AppPlaybackTarget,
        activeTargetEpoch: Long,
    ): ForegroundPlaybackAction {
        backgroundedTarget = null
        return if (foreground) {
            if (activeTarget is AppPlaybackTarget.Recording) {
                ForegroundPlaybackAction.ResumeRecording
            } else {
                ForegroundPlaybackAction.None
            }
        } else {
            rememberBackgroundedTarget(
                activeTarget = activeTarget,
                activeTargetEpoch = activeTargetEpoch,
                // Opening a target (including Resume at a saved position) is a new play request.
                recordingPlayWhenReady = true,
            )
        }
    }

    private fun rememberBackgroundedTarget(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        recordingPlayWhenReady: Boolean,
    ): ForegroundPlaybackAction {
        backgroundedTarget = when {
            activeTarget is AppPlaybackTarget.Live && activeTargetEpoch != null ->
                BackgroundedPlaybackTarget.Live(activeTarget.channelId)
            activeTarget is AppPlaybackTarget.Recording && activeTargetEpoch != null ->
                BackgroundedPlaybackTarget.Recording(
                    recordingId = activeTarget.recordingId,
                    targetEpoch = activeTargetEpoch,
                    resumeOnForeground = recordingPlayWhenReady,
                )
            else -> null
        }
        return when (backgroundedTarget) {
            is BackgroundedPlaybackTarget.Live -> ForegroundPlaybackAction.StopLive
            is BackgroundedPlaybackTarget.Recording -> ForegroundPlaybackAction.PauseRecording
            null -> ForegroundPlaybackAction.None
        }
    }
}
