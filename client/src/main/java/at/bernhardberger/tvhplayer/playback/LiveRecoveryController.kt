@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

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
    private var admittedRecovery: LiveRecoveryFence? = null

    /**
     * An admitted attempt an interruption pause stopped before it retuned: its target still owes
     * that retune. The attempt's budget is already spent, so resuming it claims none.
     */
    var interruptedRecovery: LiveRecoveryFence? = null
        private set

    /**
     * A recovery the SDK requested while an interruption pause or the viewer's Pause held its
     * target: never admitted, so it has claimed no attempt yet. It is admitted (and claims one)
     * once playback may continue.
     */
    var deferredRecovery: LiveRecoveryFence? = null
        private set
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
            admittedRecovery = null
            try {
                // Admit the attempt under the command lock, then wait for the backoff delay
                // without holding it so a channel change or Stop stays responsive meanwhile.
                val admitted = targetCommands.serialize(onClosed = { null }) {
                    admitLocked(dispatchedReason).also {
                        // Not admitted (dropped or deferred): it no longer owns the target, so a
                        // resume right after this admission must not take it for a running one.
                        if (it == null && recoveryJob === currentJob) recoveryJob = null
                    }
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
                    admittedRecovery = null
                }
            }
        }
    }

    /**
     * Continues the [interruptedRecovery] once playback may continue: the caller has validated
     * its target. It retunes at once (the interruption already spaced it from the previous
     * attempt) and claims no attempt. A recovery that is already running owns the target instead.
     */
    fun resumeInterruptedLocked(
        retryLocked: suspend (LiveRecoveryFence) -> PlaybackTargetResult?,
        publishResumedLocked: (LiveRecoveryFence) -> Unit,
    ) {
        // A fresh identity: the stopped attempt's runner must not resolve this one when it unwinds.
        val fence = interruptedRecovery?.copy() ?: return
        interruptedRecovery = null
        // The retune serves a recovery requested meanwhile too.
        deferredRecovery = null
        if (recoveryJob?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val currentJob = currentCoroutineContext().job
            try {
                recoveryAttempts.run(fence) {
                    targetCommands.serialize(onClosed = { null }) {
                        retryLocked(fence)
                    }
                }
            } finally {
                if (recoveryJob === currentJob) {
                    recoveryJob = null
                    admittedRecovery = null
                }
            }
        }
        recoveryJob = job
        // Admitted again: a further interruption before it retunes keeps it owed once more.
        admittedRecovery = fence
        // It owns the presentation from here, before its job runs: the stopped attempt may still
        // be unwinding and must not republish over the Recovering state published next.
        recoveryAttempts.claim(fence)
        job.invokeOnCompletion { recoveryAttempts.releaseUnrun(fence) }
        publishResumedLocked(fence)
        job.start()
    }

    /**
     * An attempt found the viewer's own Pause holding its target: it stays owed, without
     * a new attempt, until the viewer plays again.
     */
    fun oweAgainLocked(fence: LiveRecoveryFence) {
        interruptedRecovery = fence
    }

    /** The owed retune no longer belongs to the playing target. */
    fun dropInterruptedLocked() {
        interruptedRecovery = null
    }

    fun deferLocked(fence: LiveRecoveryFence) {
        deferredRecovery = fence
    }

    /**
     * Takes the [deferredRecovery] for admission now; a recovery that is already running owns the
     * target instead, so it is dropped then.
     */
    fun takeDeferredLocked(): LiveRecoveryFence? {
        val fence = deferredRecovery ?: return null
        deferredRecovery = null
        return fence.takeUnless { recoveryJob?.isActive == true }
    }

    fun dropDeferredLocked() {
        deferredRecovery = null
    }

    fun nextAttemptLocked(): LiveRecoveryAttempt? = recoveryBackoff.nextAttempt()

    fun admitLocked(fence: LiveRecoveryFence) {
        admittedRecovery = fence
        // The newly admitted attempt now owns the target's recovery.
        interruptedRecovery = null
        deferredRecovery = null
    }

    fun resetBackoffLocked() {
        recoveryBackoff.reset()
    }

    /**
     * Backgrounding ends an admitted or interrupted recovery, but not the job running this call.
     * Returns whether a recovery for [activeTargetEpoch] was still pending.
     */
    fun cancelOnBackgroundLocked(currentJob: Job, activeTargetEpoch: Long?): Boolean {
        val recoveryPending = activeTargetEpoch != null && (
            recoveryJob?.isActive == true && admittedRecovery?.targetEpoch == activeTargetEpoch ||
                interruptedRecovery?.targetEpoch == activeTargetEpoch ||
                deferredRecovery?.targetEpoch == activeTargetEpoch
            )
        interruptedRecovery = null
        deferredRecovery = null
        // A queued, un-admitted escalation must still reach admission and release
        // any target kept below. The SDK will not escalate that target again.
        if (admittedRecovery != null) {
            recoveryJob?.takeUnless { it === currentJob }?.cancel()
            if (recoveryJob !== currentJob) recoveryJob = null
            admittedRecovery = null
        }
        return recoveryPending
    }

    /** A stop ends any recovery; it never cancels the job running this stop. */
    fun cancelOnStopLocked(currentJob: Job) {
        recoveryJob?.takeUnless { it === currentJob }?.cancel()
        recoveryJob = null
        admittedRecovery = null
        interruptedRecovery = null
        deferredRecovery = null
    }

    /**
     * An interruption pause stops a live recovery, unless the interruption arrived inside that
     * recovery's own serialized attempt. An admitted attempt stays owed as [interruptedRecovery]:
     * the SDK does not escalate its target again, so only this obligation can still retune it.
     * An escalation still waiting for admission is left to reach it: admission then defers it
     * as [deferredRecovery].
     */
    fun cancelOnInterruptionLocked(currentJob: Job) {
        val job = recoveryJob
        if (job === currentJob) return
        if (job?.isActive == true && admittedRecovery == null) return
        if (job?.isActive == true) admittedRecovery?.let { interruptedRecovery = it }
        job?.cancel()
        recoveryJob = null
        admittedRecovery = null
    }

    /** Detach cancels any recovery and returns its job so detach can join it later. */
    fun cancelOnDetach(): Job? {
        val pendingRecovery = recoveryJob
        recoveryJob = null
        admittedRecovery = null
        interruptedRecovery = null
        deferredRecovery = null
        pendingRecovery?.cancel()
        return pendingRecovery
    }
}
