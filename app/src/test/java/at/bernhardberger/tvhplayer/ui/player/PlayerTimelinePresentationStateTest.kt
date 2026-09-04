package at.bernhardberger.tvhplayer.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvhplayer.core.ChannelPickAction
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.channelPickAction
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTimelinePresentationStateTest {
    @Test
    fun liveTimelineSameSourceSelectionKeepsSeekQueuedBehindInFlightDispatch() = runTest {
        val firstDispatch = CompletableDeferred<Unit>()
        val dispatches = mutableListOf<Long>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 5_400_000L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -600_000L,
            positionMs = -60_000L,
            liveEdgeMs = 0L,
        )
        val seek: suspend (Long) -> TimeshiftCommandResult = { deltaMs ->
            dispatches += deltaMs
            if (dispatches.size == 1) firstDispatch.await()
            TimeshiftCommandResult.ACCEPTED
        }

        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", seek)
        assertEquals(PlayerSeekPreviewPhase.PENDING, state.seekPreviewPhase(controlsVisible = false))
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf(-30_000L), dispatches)
        assertEquals(
            PlayerSeekPreviewPhase.DISPATCHED,
            state.seekPreviewPhase(controlsVisible = false),
        )

        val pickAction = channelPickAction(ChannelId(7), ChannelId(7))
        assertEquals(ChannelPickAction.CLOSE_DRAWER, pickAction)
        if (pickAction == ChannelPickAction.TUNE) state.invalidateForSourceChange()
        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", seek)
        assertEquals(-120_000L, state.preview?.decision?.targetMs)
        firstDispatch.complete(Unit)
        runCurrent()
        advanceTimeBy(400L)
        runCurrent()

        assertEquals(listOf(-30_000L, -30_000L), dispatches)
        assertEquals(-120_000L, state.preview?.decision?.targetMs)
    }

    @Test
    fun liveTimelineClampsSignedSeekAndFencesOlderCommandFeedback() = runTest {
        val dispatches = mutableListOf<Long>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 5_400_000L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val oldFeedbackToken = state.beginFeedbackOperation()

        state.queueRelativeSeek(
            state = AppTimeshiftState(
                available = true,
                bufferStartMs = -600_000L,
                positionMs = -590_000L,
                liveEdgeMs = 0L,
            ),
            requestedDeltaMs = -30_000L,
            unavailableText = "unavailable",
            clampedText = "clamped",
            seekRelative = { deltaMs ->
                dispatches += deltaMs
                TimeshiftCommandResult.ACCEPTED
            },
        )
        assertFalse(state.applyFeedback(oldFeedbackToken, "stale"))

        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf(-10_000L), dispatches)
        assertEquals("clamped", state.feedback)

        advanceTimeBy(950L)
        runCurrent()
        assertEquals(PlayerSeekPreviewPhase.NONE, state.seekPreviewPhase(controlsVisible = false))
        assertEquals("clamped", state.feedback)
    }

    @Test
    fun liveTimelineCancellationAndDisposalDoNotDispatchPendingSeek() = runTest {
        val dispatches = mutableListOf<Long>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -120_000L,
            positionMs = -60_000L,
            liveEdgeMs = 0L,
        )

        state.queueRelativeSeek(
            timeshift,
            -30_000L,
            "unavailable",
            "clamped",
        ) {
            dispatches += it
            TimeshiftCommandResult.ACCEPTED
        }
        state.cancelPendingSeek()
        advanceTimeBy(400L)
        runCurrent()
        assertTrue(dispatches.isEmpty())
        assertNull(state.preview)

        state.queueRelativeSeek(
            timeshift,
            -30_000L,
            "unavailable",
            "clamped",
        ) {
            dispatches += it
            TimeshiftCommandResult.ACCEPTED
        }
        state.dispose()
        advanceTimeBy(400L)
        runCurrent()
        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun liveTimelineSourceInvalidationDropsPendingOldCommandBeforeNewDispatch() = runTest {
        val commands = mutableListOf<String>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val timeshift = AppTimeshiftState(
            available = true,
            bufferStartMs = -120_000L,
            positionMs = -60_000L,
            liveEdgeMs = 0L,
        )

        state.queueRelativeSeek(timeshift, -30_000L, "old unavailable", "old clamped") {
            commands += "old:$it"
            TimeshiftCommandResult.ACCEPTED
        }
        state.invalidateForSourceChange()
        state.queueRelativeSeek(timeshift, 30_000L, "new unavailable", "new clamped") {
            commands += "new:$it"
            TimeshiftCommandResult.ACCEPTED
        }
        advanceTimeBy(400L)
        runCurrent()

        assertEquals(listOf("new:30000"), commands)
    }

    @Test
    fun liveTimelineSourceInvalidationDetachesNewCommandFromSuspendedOldCommand() = runTest {
        val oldDispatchResult = CompletableDeferred<TimeshiftCommandResult>()
        val oldDispatchCancelled = CompletableDeferred<Unit>()
        val commands = mutableListOf<String>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        state.showFeedback("old feedback")
        state.queueRelativeSeek(
            state = AppTimeshiftState(
                available = true,
                bufferStartMs = -120_000L,
                positionMs = -60_000L,
                liveEdgeMs = 0L,
            ),
            requestedDeltaMs = -30_000L,
            unavailableText = "unavailable",
            clampedText = "clamped",
            seekRelative = { deltaMs ->
                commands += "old:$deltaMs"
                try {
                    oldDispatchResult.await()
                } catch (error: CancellationException) {
                    oldDispatchCancelled.complete(Unit)
                    throw error
                }
            },
        )
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf("old:-30000"), commands)

        state.invalidateForSourceChange()
        runCurrent()
        assertTrue(oldDispatchCancelled.isCompleted)
        assertNull(state.preview)
        state.queueRelativeSeek(
            state = AppTimeshiftState(
                available = true,
                bufferStartMs = -300_000L,
                positionMs = -10_000L,
                liveEdgeMs = 0L,
            ),
            requestedDeltaMs = 30_000L,
            unavailableText = "new unavailable",
            clampedText = "new clamped",
            seekRelative = { deltaMs ->
                commands += "new:$deltaMs"
                TimeshiftCommandResult.ACCEPTED
            },
        )
        assertEquals(0L, state.preview?.decision?.targetMs)
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf("old:-30000", "new:10000"), commands)
        assertEquals("new clamped", state.feedback)

        oldDispatchResult.complete(TimeshiftCommandResult.REJECTED)
        runCurrent()
        assertEquals(listOf("old:-30000", "new:10000"), commands)
        assertEquals("new clamped", state.feedback)
    }

    @Test
    fun liveTimelineOwnsClockCadenceAndPlayerProgressPresentation() = runTest {
        var epochMillis = 5_400_000L
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { epochMillis },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val polling = launch { state.observeClock() }
        runCurrent()
        state.updatePlaybackProgressing(
            isPlaying = true,
            playbackState = Player.STATE_READY,
        )
        assertEquals(5_400L, state.nowEpochSec)
        assertTrue(state.playbackProgressing)

        epochMillis = 5_401_000L
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(5_400L, state.nowEpochSec)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(5_401L, state.nowEpochSec)

        state.updatePlaybackProgressing(
            isPlaying = true,
            playbackState = Player.STATE_BUFFERING,
        )
        assertFalse(state.playbackProgressing)
        polling.cancel()
    }

    @Test
    fun recordingTimelineStacksAbsoluteTargetsAndSettlesFeedback() = runTest {
        var currentPositionMs = 60_000L
        var durationMs = 120_000L
        var settled = false
        val seeks = mutableListOf<Long>()
        val state = RecordingTimelinePresentationState(
            scope = this,
            currentPositionMs = { currentPositionMs },
            currentDurationMs = { durationMs },
            currentIsPlaying = { true },
            currentEpochMillis = { 5_400_000L },
            seekAbsolute = seeks::add,
            feedbackSettled = { settled },
        )

        state.queueSeek(-30_000L)
        advanceTimeBy(100L)
        state.queueSeek(-60_000L)
        assertEquals(60_000L, state.pendingOriginMs)
        assertEquals(0L, state.pendingTargetMs)
        advanceTimeBy(399L)
        runCurrent()
        assertTrue(seeks.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(0L), seeks)
        assertEquals(
            PlayerSeekPreviewPhase.DISPATCHED,
            state.seekPreviewPhase(controlsVisible = false),
        )

        advanceTimeBy(600L)
        runCurrent()
        settled = true
        advanceTimeBy(100L)
        runCurrent()
        advanceTimeBy(350L)
        runCurrent()
        assertNull(state.pendingTargetMs)
        assertNull(state.pendingOriginMs)
        assertEquals(PlayerSeekPreviewPhase.NONE, state.seekPreviewPhase(controlsVisible = false))

        currentPositionMs = 90_000L
        durationMs = C.TIME_UNSET
        state.queueSeek(300_000L)
        assertEquals(390_000L, state.pendingTargetMs)
    }

    @Test
    fun recordingTimelinePollsSourceTruthAndProjectsPendingTarget() = runTest {
        var currentPositionMs = 15_000L
        var durationMs = C.TIME_UNSET
        var isPlaying = false
        var epochMillis = 5_400_000L
        val state = RecordingTimelinePresentationState(
            scope = this,
            currentPositionMs = { currentPositionMs },
            currentDurationMs = { durationMs },
            currentIsPlaying = { isPlaying },
            currentEpochMillis = { epochMillis },
            seekAbsolute = {},
            feedbackSettled = { true },
        )
        val polling = launch { state.observePlayback() }
        runCurrent()
        assertEquals(15_000L, state.positionMs)
        assertEquals(C.TIME_UNSET, state.durationMs)
        assertFalse(state.isPlaying)
        assertEquals(5_400L, state.nowEpochSec)

        state.queueSeek(30_000L)
        currentPositionMs = 20_000L
        durationMs = 120_000L
        isPlaying = true
        epochMillis = 5_401_000L
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(45_000L, state.positionMs)
        assertEquals(120_000L, state.durationMs)
        assertTrue(state.isPlaying)
        assertEquals(5_401L, state.nowEpochSec)

        state.cancelPendingSeek()
        currentPositionMs = 25_000L
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(25_000L, state.positionMs)
        polling.cancel()
    }

    @Test
    fun recordingTimelineCancellationAndDisposalPreventAbsoluteDispatch() = runTest {
        val seeks = mutableListOf<Long>()
        val state = RecordingTimelinePresentationState(
            scope = this,
            currentPositionMs = { 60_000L },
            currentDurationMs = { 120_000L },
            currentIsPlaying = { true },
            currentEpochMillis = { 0L },
            seekAbsolute = seeks::add,
            feedbackSettled = { true },
        )

        state.queueSeek(-30_000L)
        state.cancelPendingSeek()
        advanceTimeBy(400L)
        runCurrent()
        assertTrue(seeks.isEmpty())

        state.queueSeek(-30_000L)
        state.dispose()
        advanceTimeBy(400L)
        runCurrent()
        assertTrue(seeks.isEmpty())
    }
}
