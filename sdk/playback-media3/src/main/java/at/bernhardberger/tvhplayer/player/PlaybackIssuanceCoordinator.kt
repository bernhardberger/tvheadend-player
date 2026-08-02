package at.bernhardberger.tvhplayer.player

import at.bernhardberger.tvhplayer.core.PlaybackIntent
import at.bernhardberger.tvhplayer.core.PlaybackIssuanceState
import at.bernhardberger.tvhplayer.core.PlaybackRejectionReason
import at.bernhardberger.tvhplayer.core.PlaybackSubmissionDecision
import at.bernhardberger.tvhplayer.core.PlaybackTeardownBarrier
import at.bernhardberger.tvhplayer.core.completePlaybackIssuance
import at.bernhardberger.tvhplayer.core.completePlaybackTeardown
import at.bernhardberger.tvhplayer.core.failPlaybackTeardown
import at.bernhardberger.tvhplayer.core.playbackIntentMayCommit
import at.bernhardberger.tvhplayer.core.submitPlaybackIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PlaybackIssuanceTicket(
    val decision: PlaybackSubmissionDecision,
    val completion: Deferred<Unit>,
)

internal class PlaybackIssuanceCoordinator {
    private data class LiveRequest(
        val sequence: Long,
        val serviceId: Int,
    )

    private data class LiveSubmission(
        val request: LiveRequest,
        val ticket: PlaybackIssuanceTicket,
        val stopBarrierEpoch: Long?,
    )

    private val lock = Any()
    private var state = PlaybackIssuanceState()
    private val completions = mutableMapOf<Long, CompletableDeferred<Unit>>()
    private var liveRequestSequence = 0L
    private var latestLiveRequest: LiveRequest? = null

    fun submit(intent: PlaybackIntent): PlaybackIssuanceTicket = synchronized(lock) {
        submitLocked(intent)
    }

    suspend fun submitLive(serviceId: Int): PlaybackIssuanceTicket? {
        val callerContext = currentCoroutineContext()
        val intent = PlaybackIntent.Live(serviceId)
        val submission = synchronized(lock) {
            val request = latestLiveRequest
                ?.takeIf { it.serviceId == serviceId }
                ?: LiveRequest(++liveRequestSequence, serviceId).also {
                    latestLiveRequest = it
                }
            val stopBarrierEpoch = state.epoch.takeIf {
                state.barrier == PlaybackTeardownBarrier.STOP &&
                    !state.teardownRetryable &&
                    completions.containsKey(state.epoch)
            }
            LiveSubmission(
                request = request,
                ticket = submitLocked(intent),
                stopBarrierEpoch = stopBarrierEpoch,
            )
        }

        val rejection = submission.ticket.decision as? PlaybackSubmissionDecision.Reject
            ?: return submission.ticket
        if (
            rejection.reason != PlaybackRejectionReason.TEARDOWN_IN_PROGRESS ||
            submission.stopBarrierEpoch == null
        ) return null

        submission.ticket.completion.await()
        callerContext.ensureActive()
        return synchronized(lock) {
            callerContext.ensureActive()
            if (latestLiveRequest != submission.request) return@synchronized null
            submitLocked(intent).takeUnless {
                it.decision is PlaybackSubmissionDecision.Reject
            }
        }
    }

    private fun submitLocked(intent: PlaybackIntent): PlaybackIssuanceTicket {
        val submission = submitPlaybackIntent(state, intent)
        state = submission.state
        val completion = when (val decision = submission.decision) {
            is PlaybackSubmissionDecision.Issue ->
                CompletableDeferred<Unit>().also { completions[decision.epoch] = it }
            is PlaybackSubmissionDecision.Join ->
                completions[decision.epoch] ?: completedDeferred()
            is PlaybackSubmissionDecision.Reject -> when (decision.reason) {
                PlaybackRejectionReason.TEARDOWN_IN_PROGRESS ->
                    completions[state.epoch] ?: completedDeferred()
                PlaybackRejectionReason.STALE_RETRY,
                PlaybackRejectionReason.RELEASED -> completedDeferred()
            }
        }
        return PlaybackIssuanceTicket(submission.decision, completion)
    }

    fun mayCommit(epoch: Long): Boolean = synchronized(lock) {
        playbackIntentMayCommit(state, epoch)
    }

    fun <T> commitIfCurrent(epoch: Long, block: () -> T): T? = synchronized(lock) {
        if (!playbackIntentMayCommit(state, epoch)) return@synchronized null
        block()
    }

    fun complete(epoch: Long) {
        val completion = synchronized(lock) {
            state = completePlaybackIssuance(state, epoch)
            completions.remove(epoch)
        }
        completion?.complete(Unit)
    }

    fun completeTeardown(epoch: Long) {
        val completion = synchronized(lock) {
            state = completePlaybackTeardown(state, epoch)
            completions.remove(epoch)
        }
        completion?.complete(Unit)
    }

    fun failTeardown(epoch: Long, error: Throwable) {
        val completion = synchronized(lock) {
            state = failPlaybackTeardown(state, epoch)
            completions.remove(epoch)
        }
        completion?.completeExceptionally(error)
    }

    fun currentEpoch(): Long = synchronized(lock) { state.epoch }

    fun isReleased(): Boolean = synchronized(lock) { state.released }

    private fun completedDeferred(): Deferred<Unit> =
        CompletableDeferred<Unit>().apply { complete(Unit) }
}
