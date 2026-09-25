@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job

/**
 * [AppPlaybackRuntime]'s live recovery bookkeeping: the dispatched recovery job, the epoch whose
 * attempt was admitted, the attempt budget and the attempt runner. The runtime decides admission
 * and retunes; each way of ending a recovery keeps its own cancel below, and they differ on purpose.
 */
internal class LiveRecoveryController(
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    publishResolvedRecoveryPlayerState: (LiveRecoveryFence, PlaybackTargetResult?) -> Unit,
) {
    private var recoveryJob: Job? = null
    private var admittedRecoveryEpoch: Long? = null
    private val recoveryBackoff = LiveRecoveryBackoff()
    val recoveryAttempts = LiveRecoveryAttemptRunner(publishResolvedRecoveryPlayerState)

    /**
     * Runs one recovery in its own dispatched coroutine: [admitLocked] and [retryLocked] run
     * under the command lock, in that coroutine, so its job is the current job there.
     */
    fun dispatch(
        reason: PlaybackRecoveryReason,
        admitLocked: suspend (PlaybackRecoveryReason) -> Pair<LiveRecoveryFence, LiveRecoveryAttempt>?,
        retryLocked: suspend (LiveRecoveryFence) -> PlaybackTargetResult?,
    ) {
        dispatchPlaybackRecovery(scope, reason) { dispatchedReason ->
            val currentJob = currentCoroutineContext().job
            recoveryJob?.takeUnless { it === currentJob }?.cancel()
            recoveryJob = currentJob
            admittedRecoveryEpoch = null
            try {
                // Admit the attempt under the command lock, then wait for the backoff delay
                // without holding it so a channel change or Stop stays responsive meanwhile.
                val admitted = targetCommands.serialize(onClosed = { null }) {
                    admitLocked(dispatchedReason)
                }
                if (admitted != null) {
                    val (fence, attempt) = admitted
                    recoveryAttempts.run(fence) {
                        if (attempt.delayMillis > 0L) delay(attempt.delayMillis)
                        targetCommands.serialize(onClosed = { null }) {
                            retryLocked(fence)
                        }
                    }
                }
            } finally {
                if (recoveryJob === currentJob) {
                    recoveryJob = null
                    admittedRecoveryEpoch = null
                }
            }
        }
    }

    fun nextAttemptLocked(): LiveRecoveryAttempt? = recoveryBackoff.nextAttempt()

    fun admitLocked(targetEpoch: Long) {
        admittedRecoveryEpoch = targetEpoch
    }

    fun resetBackoffLocked() {
        recoveryBackoff.reset()
    }

    /**
     * Backgrounding ends an admitted recovery, but not the job running this call. Returns
     * whether an admitted recovery for [activeTargetEpoch] was still pending.
     */
    fun cancelOnBackgroundLocked(currentJob: Job, activeTargetEpoch: Long?): Boolean {
        val recoveryPending = recoveryJob?.isActive == true && activeTargetEpoch != null &&
            admittedRecoveryEpoch == activeTargetEpoch
        // A queued, un-admitted escalation must still reach admission and release
        // any target kept below. The SDK will not escalate that target again.
        if (admittedRecoveryEpoch != null) {
            recoveryJob?.takeUnless { it === currentJob }?.cancel()
            if (recoveryJob !== currentJob) recoveryJob = null
            admittedRecoveryEpoch = null
        }
        return recoveryPending
    }

    /** A stop ends any recovery; it never cancels the job running this stop. */
    fun cancelOnStopLocked(currentJob: Job) {
        recoveryJob?.takeUnless { it === currentJob }?.cancel()
        recoveryJob = null
        admittedRecoveryEpoch = null
    }

    /**
     * An interruption pause ends a live recovery, unless the interruption arrived inside that
     * recovery's own serialized attempt.
     */
    fun cancelOnInterruptionLocked(currentJob: Job) {
        recoveryJob?.takeUnless { it === currentJob }?.cancel()
        if (recoveryJob !== currentJob) {
            recoveryJob = null
            admittedRecoveryEpoch = null
        }
    }

    /** Detach cancels any recovery and returns its job so detach can join it later. */
    fun cancelOnDetach(): Job? {
        val pendingRecovery = recoveryJob
        recoveryJob = null
        admittedRecoveryEpoch = null
        pendingRecovery?.cancel()
        return pendingRecovery
    }
}
