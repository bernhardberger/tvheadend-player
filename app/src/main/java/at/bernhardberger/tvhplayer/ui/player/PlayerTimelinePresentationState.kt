package at.bernhardberger.tvhplayer.ui.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import kotlin.time.Duration.Companion.milliseconds
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.TimeshiftSeekQueueState
import at.bernhardberger.tvhplayer.core.beginTimeshiftSeekDispatch
import at.bernhardberger.tvhplayer.core.cancelPendingTimeshiftSeek
import at.bernhardberger.tvhplayer.core.completeTimeshiftSeekDispatch
import at.bernhardberger.tvhplayer.core.playerPlaybackProgressing
import at.bernhardberger.tvhplayer.core.queueTimeshiftSeek
import at.bernhardberger.tvhplayer.core.queuedTimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.recordingSeekFeedbackSettled
import at.bernhardberger.tvhplayer.core.recordingStackedSeekTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TIMESHIFT_SEEK_DEBOUNCE_MS = 400L
private const val TIMESHIFT_SEEK_FEEDBACK_MS = 950L
private const val RECORDING_POSITION_POLL_MS = 250L
private const val RECORDING_SEEK_DEBOUNCE_MS = 400L
private const val RECORDING_SEEK_FEEDBACK_MIN_MS = 600L
private const val RECORDING_SEEK_FEEDBACK_POLL_MS = 100L
private const val RECORDING_SEEK_FEEDBACK_SETTLED_GRACE_MS = 350L

internal data class LiveTimeshiftSeekPreview(
    val token: Int,
    val feedbackToken: Long,
    val decision: TimeshiftSeekDecision,
    val dispatched: Boolean,
    val target: TimeshiftContentTarget,
)

private class LiveTimelineSourceGeneration(
    initialFeedbackToken: Long,
) {
    var seekQueue by mutableStateOf(TimeshiftSeekQueueState())
    var selectionTimeline: TimeshiftTimeline? = null
    var pendingContentTarget: TimeshiftContentTarget? = null
    var seekJob: Job? = null
    var seekFeedbackJob: Job? = null
    var seekToken = 0
    var seekQueuedAtMs = 0L
    var commitRequested = false
    val seekWake = Channel<Unit>(Channel.CONFLATED)
    var feedback by mutableStateOf<String?>(null)
    var preview by mutableStateOf<LiveTimeshiftSeekPreview?>(null)
    var feedbackToken by mutableLongStateOf(initialFeedbackToken)
}

internal class LiveTimelinePresentationState(
    private val scope: CoroutineScope,
    currentEpochMillis: () -> Long = System::currentTimeMillis,
    private val monotonicTimeMillis: () -> Long = SystemClock::uptimeMillis,
) {
    private val currentEpochMillis = currentEpochMillis
    private var sourceGeneration by mutableStateOf(LiveTimelineSourceGeneration(0L))
    private val sourceGenerations = mutableSetOf(sourceGeneration)
    private var disposed = false

    var nowEpochSec by mutableLongStateOf(currentEpochMillis() / 1_000L)
        private set
    var playbackProgressing by mutableStateOf(false)
        private set
    val feedback: String?
        get() = sourceGeneration.feedback
    val preview: LiveTimeshiftSeekPreview?
        get() = sourceGeneration.preview
    val feedbackToken: Long
        get() = sourceGeneration.feedbackToken

    val seekPending: Boolean
        get() = sourceGeneration.seekQueue.pendingDeltaMs != 0L

    fun seekPreviewPhase(controlsVisible: Boolean): PlayerSeekPreviewPhase = when {
        preview == null -> PlayerSeekPreviewPhase.NONE
        requireNotNull(preview).dispatched -> PlayerSeekPreviewPhase.DISPATCHED
        else -> PlayerSeekPreviewPhase.PENDING
    }

    fun beginFeedbackOperation(): Long {
        sourceGeneration.feedbackToken += 1L
        return sourceGeneration.feedbackToken
    }

    fun applyFeedback(token: Long, value: String?): Boolean {
        val generation = sourceGeneration
        if (token != generation.feedbackToken) return false
        generation.feedback = value
        return true
    }

    fun showFeedback(value: String?) {
        sourceGeneration.feedback = value
    }

    fun clearFeedback() {
        sourceGeneration.feedback = null
    }

    fun queueRelativeSeek(
        state: AppTimeshiftState,
        requestedDeltaMs: Long,
        unavailableText: String,
        clampedText: String,
        expiredText: String,
        replacedText: String,
        uncertainText: String,
        seekContent: suspend (TimeshiftContentTarget) -> TimeshiftContentSeekResult,
    ) {
        if (disposed || !state.available || !state.timingKnown) return
        val generation = sourceGeneration
        if (generation.preview == null && requestedDeltaMs < 0L && state.positionMs <= state.bufferStartMs) return
        val selectionTimeline = generation.selectionTimeline ?: state.timeline ?: return
        generation.selectionTimeline = selectionTimeline
        val nextQueue = queueTimeshiftSeek(
            queue = generation.seekQueue,
            state = state.copy(
                bufferStartMs = selectionTimeline.start.inWholeMilliseconds,
                liveEdgeMs = selectionTimeline.end.inWholeMilliseconds,
            ),
            requestedDeltaMs = requestedDeltaMs,
        )
        val decision = queuedTimeshiftSeekDecision(nextQueue)
        val target = selectionTimeline.select(decision.targetMs.milliseconds) ?: return
        val operationFeedbackToken = beginFeedbackOperation()
        generation.seekQueue = nextQueue
        generation.seekToken++
        val token = generation.seekToken
        generation.pendingContentTarget = target
        generation.seekQueuedAtMs = monotonicTimeMillis()
        generation.preview = LiveTimeshiftSeekPreview(
            token = token,
            feedbackToken = operationFeedbackToken,
            decision = decision,
            dispatched = false,
            target = target,
        )
        generation.seekFeedbackJob?.cancel()
        generation.seekFeedbackJob = null
        if (generation.seekJob?.isActive == true) return
        generation.seekJob = scope.launch {
            try {
                while (true) {
                    val debounceRemainingMs = (
                        generation.seekQueuedAtMs + TIMESHIFT_SEEK_DEBOUNCE_MS -
                            monotonicTimeMillis()
                        ).coerceAtLeast(0L)
                    if (!generation.commitRequested && debounceRemainingMs > 0L) {
                        withTimeoutOrNull(debounceRemainingMs) { generation.seekWake.receive() }
                        // Repeats can move the deadline while this owner is asleep.
                        continue
                    }

                    generation.commitRequested = false
                    val dispatch = beginTimeshiftSeekDispatch(generation.seekQueue)
                    if (dispatch == null) {
                        generation.seekQueue = cancelPendingTimeshiftSeek(generation.seekQueue)
                        if (generation.preview?.dispatched == false) generation.preview = null
                        break
                    }
                    generation.seekQueue = dispatch.queue
                    val dispatchToken = generation.preview?.token ?: generation.seekToken
                    val dispatchFeedbackToken = generation.preview?.feedbackToken
                        ?: generation.feedbackToken
                    generation.preview = generation.preview
                        ?.takeIf { it.token == dispatchToken }
                        ?.copy(dispatched = true)

                    val contentTarget = generation.pendingContentTarget ?: break
                    generation.pendingContentTarget = null
                    val result = seekContent(contentTarget)
                    val command = (result as? TimeshiftContentSeekResult.Completed)?.command
                    val accepted = command?.disposition == TimeshiftCommandDisposition.ACCEPTED
                    generation.seekQueue = completeTimeshiftSeekDispatch(
                        generation.seekQueue,
                        accepted,
                    )
                    if (generation !== sourceGeneration) break
                    if (generation.preview?.token == dispatchToken || !accepted) {
                        // Rejection discards stacked input too; explain that outcome rather
                        // than silently removing the newer preview. Preserve unrelated feedback.
                        if (dispatchFeedbackToken == generation.feedbackToken ||
                            (!accepted && generation.preview?.feedbackToken == generation.feedbackToken)
                        ) {
                            generation.feedback = when {
                                command?.isOutcomeUncertain == true -> uncertainText
                                command?.isTerminal == true -> unavailableText
                                command?.isAccepted == true -> clampedText.takeIf {
                                    generation.preview?.decision?.clamped == true
                                }
                                result == TimeshiftContentSeekResult.Expired -> expiredText
                                result == TimeshiftContentSeekResult.Replaced -> replacedText
                                else -> unavailableText
                            }
                        }
                        generation.seekFeedbackJob?.cancel()
                        generation.seekFeedbackJob = scope.launch {
                            delay(TIMESHIFT_SEEK_FEEDBACK_MS)
                            if (
                                generation === sourceGeneration &&
                                generation.preview?.token == dispatchToken
                            ) {
                                generation.seekFeedbackJob = null
                                clearPreview(generation)
                            }
                            releaseGenerationIfInactive(generation)
                        }
                    }
                }
            } finally {
                if (generation.seekQueue.dispatchInFlight) {
                    generation.seekQueue = completeTimeshiftSeekDispatch(
                        generation.seekQueue,
                        accepted = false,
                    )
                }
                if (generation.preview == null) {
                    generation.selectionTimeline = null
                    generation.seekQueue = TimeshiftSeekQueueState()
                    generation.pendingContentTarget = null
                }
                generation.seekJob = null
                releaseGenerationIfInactive(generation)
            }
        }
    }

    fun cancelPendingSeek() {
        val generation = sourceGeneration
        generation.commitRequested = false
        generation.seekQueue = cancelPendingTimeshiftSeek(generation.seekQueue)
        generation.pendingContentTarget = null
        generation.seekToken++
        clearPreview(generation)
    }

    fun dismissDispatchedFeedback() {
        clearPreview(sourceGeneration)
    }

    fun commitPendingSeek() {
        val generation = sourceGeneration
        if (disposed || generation.seekQueue.pendingDeltaMs == 0L) return
        generation.commitRequested = true
        generation.seekWake.trySend(Unit)
    }

    fun invalidateForSourceChange() {
        val previousGeneration = sourceGeneration
        previousGeneration.seekJob?.cancel()
        previousGeneration.seekJob = null
        previousGeneration.seekQueue = cancelPendingTimeshiftSeek(previousGeneration.seekQueue)
        previousGeneration.seekToken++
        clearPreview(previousGeneration)

        val nextGeneration = LiveTimelineSourceGeneration(
            initialFeedbackToken = previousGeneration.feedbackToken + 1L,
        )
        sourceGenerations += nextGeneration
        sourceGeneration = nextGeneration
        releaseGenerationIfInactive(previousGeneration)
    }

    fun updatePlaybackProgressing(isPlaying: Boolean, playbackState: Int) {
        playbackProgressing = playerPlaybackProgressing(
            isPlaying = isPlaying,
            playerReady = playbackState == Player.STATE_READY,
        )
    }

    suspend fun observeClock() {
        while (true) {
            nowEpochSec = currentEpochMillis() / 1_000L
            delay(1_000L)
        }
    }

    fun dispose() {
        disposed = true
        sourceGenerations.toList().forEach { generation ->
            generation.seekJob?.cancel()
            generation.seekFeedbackJob?.cancel()
            generation.seekJob = null
            generation.seekFeedbackJob = null
        }
        sourceGenerations.clear()
    }

    private fun clearPreview(generation: LiveTimelineSourceGeneration) {
        generation.seekFeedbackJob?.cancel()
        generation.seekFeedbackJob = null
        generation.preview = null
        if (!generation.seekQueue.dispatchInFlight) {
            generation.selectionTimeline = null
            generation.seekQueue = TimeshiftSeekQueueState()
            generation.pendingContentTarget = null
        }
    }

    private fun releaseGenerationIfInactive(generation: LiveTimelineSourceGeneration) {
        if (
            generation !== sourceGeneration &&
            generation.seekJob == null &&
            generation.seekFeedbackJob == null
        ) {
            sourceGenerations -= generation
        }
    }
}

@Composable
internal fun rememberLiveTimelinePresentationState(player: Player): LiveTimelinePresentationState {
    val scope = rememberCoroutineScope()
    val state = remember(player, scope) {
        LiveTimelinePresentationState(scope = scope)
    }

    LaunchedEffect(state) {
        state.observeClock()
    }
    DisposableEffect(player, state) {
        fun updatePlaybackProgressing() {
            state.updatePlaybackProgressing(player.isPlaying, player.playbackState)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlaybackProgressing()

            override fun onPlaybackStateChanged(playbackState: Int) = updatePlaybackProgressing()
        }
        player.addListener(listener)
        updatePlaybackProgressing()
        onDispose {
            player.removeListener(listener)
            state.dispose()
        }
    }
    return state
}

internal class RecordingTimelinePresentationState(
    private val scope: CoroutineScope,
    private val currentPositionMs: () -> Long,
    private val currentDurationMs: () -> Long,
    private val currentIsPlaying: () -> Boolean,
    private val currentCanSeek: () -> Boolean,
    currentEpochMillis: () -> Long = System::currentTimeMillis,
    private val seekAbsolute: (Long) -> Unit,
    private val feedbackSettled: () -> Boolean,
) {
    private val currentEpochMillis = currentEpochMillis
    private var seekJob: Job? = null
    private var seekToken = 0
    private var disposed = false

    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(C.TIME_UNSET)
        private set
    var nowEpochSec by mutableLongStateOf(currentEpochMillis() / 1_000L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var canSeek by mutableStateOf(false)
        private set
    var pendingTargetMs by mutableStateOf<Long?>(null)
        private set
    var pendingOriginMs by mutableStateOf<Long?>(null)
        private set
    private var pendingDispatched by mutableStateOf(false)

    val seekPending: Boolean
        get() = pendingTargetMs != null

    fun seekPreviewPhase(controlsVisible: Boolean): PlayerSeekPreviewPhase = when {
        pendingTargetMs == null -> PlayerSeekPreviewPhase.NONE
        pendingDispatched -> PlayerSeekPreviewPhase.DISPATCHED
        else -> PlayerSeekPreviewPhase.PENDING
    }

    fun queueSeek(deltaMs: Long) {
        if (disposed || !currentCanSeek()) return
        val currentPosition = currentPositionMs().coerceAtLeast(0L)
        if (pendingTargetMs == null) pendingOriginMs = currentPosition
        pendingDispatched = false
        pendingTargetMs = recordingStackedSeekTarget(
            currentMs = currentPosition,
            pendingTargetMs = pendingTargetMs,
            durationMs = currentDurationMs().takeIf { it != C.TIME_UNSET && it > 0L },
            deltaMs = deltaMs,
        )
        positionMs = requireNotNull(pendingTargetMs)
        seekToken++
        val token = seekToken
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(RECORDING_SEEK_DEBOUNCE_MS)
            if (token != seekToken) return@launch
            commitPendingSeek()
            delay(RECORDING_SEEK_FEEDBACK_MIN_MS)
            while (!feedbackSettled()) {
                delay(RECORDING_SEEK_FEEDBACK_POLL_MS)
            }
            delay(RECORDING_SEEK_FEEDBACK_SETTLED_GRACE_MS)
            if (token != seekToken) return@launch
            clearPendingState()
            seekJob = null
        }
    }

    fun commitPendingSeek() {
        if (disposed || pendingDispatched) return
        val target = pendingTargetMs ?: return
        if (!currentCanSeek()) {
            clearPendingState()
            return
        }
        pendingDispatched = true
        seekAbsolute(target)
    }

    fun cancelPendingSeek() {
        seekToken++
        seekJob?.cancel()
        seekJob = null
        clearPendingState()
    }

    fun dismissDispatchedFeedback() {
        cancelPendingSeek()
    }

    suspend fun observePlayback() {
        while (true) {
            positionMs = pendingTargetMs ?: currentPositionMs().coerceAtLeast(0L)
            durationMs = currentDurationMs()
            isPlaying = currentIsPlaying()
            canSeek = currentCanSeek()
            nowEpochSec = currentEpochMillis() / 1_000L
            delay(RECORDING_POSITION_POLL_MS)
        }
    }

    fun dispose() {
        disposed = true
        seekToken++
        seekJob?.cancel()
        seekJob = null
    }

    private fun clearPendingState() {
        pendingTargetMs = null
        pendingOriginMs = null
        pendingDispatched = false
    }
}

@Composable
internal fun rememberRecordingTimelinePresentationState(
    player: Player,
    session: AppPlaybackRuntime,
    playbackAvailable: Boolean,
): RecordingTimelinePresentationState {
    val scope = rememberCoroutineScope()
    val state = remember(player, session, scope) {
        RecordingTimelinePresentationState(
            scope = scope,
            currentPositionMs = player::getCurrentPosition,
            currentDurationMs = player::getDuration,
            currentIsPlaying = player::isPlaying,
            currentCanSeek = {
                player.isCurrentMediaItemSeekable &&
                    player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            },
            seekAbsolute = session::seekTo,
            feedbackSettled = {
                recordingSeekFeedbackSettled(
                    playerReady = player.playbackState == Player.STATE_READY,
                    playerEnded = player.playbackState == Player.STATE_ENDED,
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                    playbackFailed = session.state.value is AppPlaybackState.Failed,
                )
            },
        )
    }
    LaunchedEffect(state, playbackAvailable) {
        if (playbackAvailable) state.observePlayback()
    }
    DisposableEffect(state) {
        onDispose(state::dispose)
    }
    return state
}
