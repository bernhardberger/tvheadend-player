package at.bernhardberger.tvhplayer.core

sealed interface PlaybackIntent {
    data class Live(val serviceId: Int) : PlaybackIntent

    data class RetryLive(
        val serviceId: Int,
        val expectedEpoch: Long,
    ) : PlaybackIntent

    data class Recording(
        val entryId: Int,
        val path: String,
        val startIntent: RecordingPlaybackIntent,
    ) : PlaybackIntent

    data class RetryRecording(
        val entryId: Int,
        val path: String,
        val positionSeconds: Long,
        val expectedEpoch: Long,
    ) : PlaybackIntent

    data object Stop : PlaybackIntent
    data object Release : PlaybackIntent
}

enum class PlaybackTeardownBarrier {
    STOP,
    RELEASE,
}

data class PlaybackIssuanceState(
    val epoch: Long = 0L,
    val intent: PlaybackIntent? = null,
    val barrier: PlaybackTeardownBarrier? = null,
    val teardownRetryable: Boolean = false,
    val released: Boolean = false,
)

sealed interface PlaybackSubmissionDecision {
    data class Issue(val epoch: Long) : PlaybackSubmissionDecision
    data class Join(val epoch: Long) : PlaybackSubmissionDecision
    data class Reject(val reason: PlaybackRejectionReason) : PlaybackSubmissionDecision
}

enum class PlaybackRejectionReason {
    STALE_RETRY,
    TEARDOWN_IN_PROGRESS,
    RELEASED,
}

data class PlaybackSubmission(
    val state: PlaybackIssuanceState,
    val decision: PlaybackSubmissionDecision,
)

fun submitPlaybackIntent(
    state: PlaybackIssuanceState,
    intent: PlaybackIntent,
): PlaybackSubmission {
    if (state.released) {
        return PlaybackSubmission(
            state = state,
            decision = PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.RELEASED),
        )
    }

    state.barrier?.let { barrier ->
        val retryBarrier = when (intent) {
            PlaybackIntent.Stop -> barrier.takeIf { it == PlaybackTeardownBarrier.STOP }
            PlaybackIntent.Release -> PlaybackTeardownBarrier.RELEASE
            else -> null
        }
        if (state.teardownRetryable && retryBarrier != null) {
            val nextEpoch = state.epoch + 1L
            return PlaybackSubmission(
                state = state.copy(
                    epoch = nextEpoch,
                    intent = intent,
                    barrier = retryBarrier,
                    teardownRetryable = false,
                ),
                decision = PlaybackSubmissionDecision.Issue(nextEpoch),
            )
        }
        val joinsBarrier = when (intent) {
            PlaybackIntent.Stop -> barrier == PlaybackTeardownBarrier.STOP
            PlaybackIntent.Release -> barrier == PlaybackTeardownBarrier.RELEASE
            else -> false
        }
        return PlaybackSubmission(
            state = state,
            decision = if (joinsBarrier) {
                PlaybackSubmissionDecision.Join(state.epoch)
            } else {
                PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.TEARDOWN_IN_PROGRESS)
            },
        )
    }

    if (playbackIntentsAreEquivalent(state.intent, intent)) {
        return PlaybackSubmission(
            state = state,
            decision = PlaybackSubmissionDecision.Join(state.epoch),
        )
    }

    if (intent is PlaybackIntent.RetryLive && !intent.matchesCurrent(state)) {
        return PlaybackSubmission(
            state = state,
            decision = PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.STALE_RETRY),
        )
    }
    if (intent is PlaybackIntent.RetryRecording && !intent.matchesCurrent(state)) {
        return PlaybackSubmission(
            state = state,
            decision = PlaybackSubmissionDecision.Reject(PlaybackRejectionReason.STALE_RETRY),
        )
    }

    val nextEpoch = state.epoch + 1L
    val barrier = when (intent) {
        PlaybackIntent.Stop -> PlaybackTeardownBarrier.STOP
        PlaybackIntent.Release -> PlaybackTeardownBarrier.RELEASE
        else -> null
    }
    return PlaybackSubmission(
        state = state.copy(
            epoch = nextEpoch,
            intent = intent,
            barrier = barrier,
            teardownRetryable = false,
        ),
        decision = PlaybackSubmissionDecision.Issue(nextEpoch),
    )
}

fun completePlaybackTeardown(
    state: PlaybackIssuanceState,
    epoch: Long,
): PlaybackIssuanceState {
    if (state.epoch != epoch) return state
    return when (state.barrier) {
        PlaybackTeardownBarrier.STOP -> state.copy(
            intent = null,
            barrier = null,
            teardownRetryable = false,
        )
        PlaybackTeardownBarrier.RELEASE -> state.copy(
            intent = null,
            barrier = null,
            teardownRetryable = false,
            released = true,
        )
        null -> state
    }
}

fun failPlaybackTeardown(
    state: PlaybackIssuanceState,
    epoch: Long,
): PlaybackIssuanceState {
    if (state.epoch != epoch) return state
    return when (state.barrier) {
        PlaybackTeardownBarrier.STOP,
        PlaybackTeardownBarrier.RELEASE -> state.copy(teardownRetryable = true)
        null -> state
    }
}

fun completePlaybackIssuance(
    state: PlaybackIssuanceState,
    epoch: Long,
): PlaybackIssuanceState {
    if (state.epoch != epoch || state.barrier != null) return state
    val settledIntent = when (val intent = state.intent) {
        is PlaybackIntent.RetryLive -> PlaybackIntent.Live(intent.serviceId)
        is PlaybackIntent.RetryRecording -> PlaybackIntent.Recording(
            entryId = intent.entryId,
            path = intent.path,
            startIntent = RecordingPlaybackIntent.Resume(intent.positionSeconds),
        )
        else -> intent
    }
    return state.copy(intent = settledIntent)
}

fun playbackIntentMayCommit(state: PlaybackIssuanceState, epoch: Long): Boolean =
    !state.released &&
        state.barrier == null &&
        state.epoch == epoch &&
        state.intent != null

private fun playbackIntentsAreEquivalent(
    current: PlaybackIntent?,
    requested: PlaybackIntent,
): Boolean = when (requested) {
    is PlaybackIntent.Live -> when (current) {
        is PlaybackIntent.Live -> current.serviceId == requested.serviceId
        is PlaybackIntent.RetryLive -> current.serviceId == requested.serviceId
        else -> false
    }
    is PlaybackIntent.RetryLive ->
        current is PlaybackIntent.RetryLive &&
            current.serviceId == requested.serviceId &&
            current.expectedEpoch == requested.expectedEpoch
    is PlaybackIntent.Recording -> when (current) {
        is PlaybackIntent.Recording ->
            current.entryId == requested.entryId &&
                current.path == requested.path &&
                current.startIntent == requested.startIntent
        is PlaybackIntent.RetryRecording ->
            requested.startIntent == RecordingPlaybackIntent.DefaultPolicy &&
                current.entryId == requested.entryId &&
                current.path == requested.path
        else -> false
    }
    is PlaybackIntent.RetryRecording ->
        current is PlaybackIntent.RetryRecording &&
            current.entryId == requested.entryId &&
            current.path == requested.path &&
            current.expectedEpoch == requested.expectedEpoch
    PlaybackIntent.Stop -> current == PlaybackIntent.Stop
    PlaybackIntent.Release -> current == PlaybackIntent.Release
}

private fun PlaybackIntent.RetryLive.matchesCurrent(state: PlaybackIssuanceState): Boolean =
    state.epoch == expectedEpoch && when (val current = state.intent) {
        is PlaybackIntent.Live -> current.serviceId == serviceId
        is PlaybackIntent.RetryLive -> current.serviceId == serviceId
        else -> false
    }

private fun PlaybackIntent.RetryRecording.matchesCurrent(state: PlaybackIssuanceState): Boolean =
    state.epoch == expectedEpoch && when (val current = state.intent) {
        is PlaybackIntent.Recording -> current.entryId == entryId && current.path == path
        is PlaybackIntent.RetryRecording -> current.entryId == entryId && current.path == path
        else -> false
    }
