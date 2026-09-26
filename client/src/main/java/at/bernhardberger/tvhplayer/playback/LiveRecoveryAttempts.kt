@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class LiveRecoveryFence(
    val reason: PlaybackRecoveryReason,
    val selection: LivePlaybackSelection,
    val targetEpoch: Long,
) {
    fun matches(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        observation: SessionObservation,
    ): Boolean {
        val currentSelection = currentLivePlaybackSelection(
            observation = observation,
            channelId = selection.channelId,
        ) ?: return false
        return activeTarget == AppPlaybackTarget.Live(selection.channelId) &&
            activeTargetEpoch == targetEpoch &&
            currentSelection.currentSession === selection.currentSession
    }
}

internal fun currentLiveRecoveryFence(
    reason: PlaybackRecoveryReason,
    observation: SessionObservation,
    activeTarget: AppPlaybackTarget?,
    activeTargetEpoch: Long?,
): LiveRecoveryFence? {
    val liveTarget = activeTarget as? AppPlaybackTarget.Live ?: return null
    val epoch = activeTargetEpoch ?: return null
    val selection = currentLivePlaybackSelection(observation, liveTarget.channelId) ?: return null
    return LiveRecoveryFence(reason, selection, epoch)
}

internal fun dispatchPlaybackRecovery(
    scope: CoroutineScope,
    reason: PlaybackRecoveryReason,
    recover: suspend (PlaybackRecoveryReason) -> Unit,
): Job = scope.launch { recover(reason) }

internal fun shouldRepublishPlayerStateAfterRecovery(
    result: PlaybackTargetResult?,
    fence: LiveRecoveryFence,
    activeTarget: AppPlaybackTarget?,
    activeTargetEpoch: Long?,
    observation: SessionObservation,
    healthyActiveTarget: AppPlaybackTarget?,
): Boolean =
    result?.isStarted != true &&
        healthyActiveTarget != null &&
        healthyActiveTarget == activeTarget &&
        fence.matches(activeTarget, activeTargetEpoch, observation)

internal class LiveRecoveryAttemptRunner(
    private val onResolved: (LiveRecoveryFence, PlaybackTargetResult?) -> Unit,
) {
    private var current: LiveRecoveryFence? = null

    /**
     * True while an attempt owns exactly the target described by the arguments.
     *
     * Recovery holds back player state so a retune does not flicker through the states of the
     * target it is replacing. That must stay scoped to the owned target: an attempt waiting out
     * its backoff would otherwise suppress the state of a different target the user has since
     * selected, leaving a playing channel presented as still starting.
     */
    fun ownsPlayerState(
        activeTarget: AppPlaybackTarget?,
        activeTargetEpoch: Long?,
        observation: SessionObservation,
    ): Boolean = current?.matches(activeTarget, activeTargetEpoch, observation) == true

    /**
     * Makes [fence] the owned attempt before its job runs, so a superseded attempt that is still
     * unwinding no longer resolves (and republishes over) the presentation [fence] publishes.
     */
    fun claim(fence: LiveRecoveryFence) {
        current = fence
    }

    /** Resolves a claimed [fence] whose job ended without running it. */
    fun releaseUnrun(fence: LiveRecoveryFence) {
        if (current === fence) {
            current = null
            onResolved(fence, null)
        }
    }

    suspend fun run(
        fence: LiveRecoveryFence,
        recover: suspend () -> PlaybackTargetResult?,
    ) {
        current = fence
        var result: PlaybackTargetResult? = null
        try {
            result = recover()
        } finally {
            if (current === fence) {
                current = null
                onResolved(fence, result)
            }
        }
    }
}
