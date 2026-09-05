package at.bernhardberger.tvhplayer.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
    private fun fixture() = TimeshiftTestFixture(600.seconds).apply {
        updateHistory(start = 0.seconds, end = 600.seconds)
    }

    private fun TimeshiftTestFixture.presentation(positionMs: Long = 540_000L) =
        state.value.toAppPresentation(playbackPosition(positionMs.milliseconds))

    @Test
    fun contentSelectionStaysAbsoluteWhileTheLiveEdgeAdvances() = runTest {
        val fixture = fixture()
        val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
        val dispatches = mutableListOf<Long>()
        owner.queueRelativeSeek(fixture.presentation(), -30_000L,
            "unavailable", "clamped", "expired", "replaced", "uncertain") { target ->
            fixture.seek(target) {
                dispatches += target.position.inWholeMilliseconds
                fixture.completed(readerReached = null)
            }
        }
        fixture.updateHistory(10.seconds, 610.seconds)
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf(510_000L), dispatches)
        assertNull(owner.feedback)
    }

    @Test
    fun expiredUnavailableAndReplacedSelectionsNeverDispatchOrClamp() = runTest {
        for (outcome in listOf("expired", "unavailable", "replaced")) {
            val fixture = fixture()
            val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
            owner.queueRelativeSeek(fixture.presentation(), -30_000L,
                "unavailable", "clamped", "expired", "replaced", "uncertain") { target ->
                fixture.seek(target) { error("Invalid content must not dispatch") }
            }
            when (outcome) {
                "expired" -> fixture.updateHistory(520.seconds, 620.seconds)
                "unavailable" -> fixture.updateHistory(null, null)
                "replaced" -> fixture.replaceSubscription()
            }
            advanceTimeBy(400L)
            runCurrent()
            assertEquals(outcome, owner.feedback)
            owner.dispose()
        }
    }

    @Test
    fun sampledPlaybackDoesNotFollowHistoryAndDoesNotExtendSeekPermission() = runTest {
        val fixture = fixture()
        val pausedSample = fixture.playbackPosition(500.seconds)
        fixture.updateHistory(520.seconds, 620.seconds)
        val presentation = fixture.state.value.toAppPresentation(pausedSample)
        assertEquals(500_000L, presentation.positionMs)
        assertEquals(520_000L, presentation.bufferStartMs)
        assertEquals(620_000L, presentation.liveEdgeMs)
        val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
        owner.queueRelativeSeek(presentation, -30_000L,
            "unavailable", "clamped", "expired", "replaced", "uncertain") {
            error("Expired played content cannot extend seekable history")
        }
        advanceTimeBy(400L)
        runCurrent()
        assertNull(owner.preview)
        assertFalse(owner.seekPending)
    }

    @Test
    fun liveCommitWakesDebounceWithoutOvertakingAnInFlightSeek() = runTest {
        val release = CompletableDeferred<Unit>()
        val dispatches = mutableListOf<Long>()
        val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
        val fixture = fixture()
        val timing = fixture.presentation()
        val seek: suspend (TimeshiftContentTarget) -> TimeshiftContentSeekResult = { target -> fixture.seek(target) {
            dispatches += target.position.inWholeMilliseconds
            if (dispatches.size == 1) release.await()
            fixture.completed()
        } }
        owner.queueRelativeSeek(timing, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        runCurrent()
        advanceTimeBy(100L)
        owner.commitPendingSeek()
        runCurrent()
        assertEquals(listOf(510_000L), dispatches)
        owner.queueRelativeSeek(timing, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        assertEquals(PlayerSeekPreviewPhase.PENDING, owner.seekPreviewPhase(controlsVisible = true))
        owner.commitPendingSeek()
        runCurrent()
        assertEquals(1, dispatches.size)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(510_000L, 480_000L), dispatches)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test
    fun recordingCommitIsImmediateAndNotRepeatedByTheIdleTimer() = runTest {
        val dispatches = mutableListOf<Long>()
        val owner = RecordingTimelinePresentationState(
            scope = this, currentPositionMs = { 60_000L }, currentDurationMs = { 600_000L },
            currentIsPlaying = { false }, currentEpochMillis = { 0L },
            currentCanSeek = { true },
            seekAbsolute = { dispatches += it }, feedbackSettled = { true },
        )
        owner.queueSeek(30_000L)
        assertEquals(PlayerSeekPreviewPhase.PENDING, owner.seekPreviewPhase(controlsVisible = true))
        owner.commitPendingSeek()
        assertEquals(listOf(90_000L), dispatches)
        assertEquals(PlayerSeekPreviewPhase.DISPATCHED, owner.seekPreviewPhase(controlsVisible = true))
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(listOf(90_000L), dispatches)
    }

    @Test
    fun recordingSeeksRequireCapabilityAtInputAndDispatch() = runTest {
        var canSeek = false
        val owner = RecordingTimelinePresentationState(
            scope = this,
            currentPositionMs = { 60_000L }, currentDurationMs = { 600_000L },
            currentIsPlaying = { false }, currentCanSeek = { canSeek },
            currentEpochMillis = { 0L },
            seekAbsolute = { error("Unavailable seek capability must not dispatch") },
            feedbackSettled = { true },
        )
        owner.queueSeek(30_000L)
        assertNull(owner.pendingTargetMs)
        canSeek = true
        owner.queueSeek(30_000L)
        assertEquals(90_000L, owner.pendingTargetMs)
        canSeek = false
        owner.commitPendingSeek()
        assertNull(owner.pendingTargetMs)
        advanceTimeBy(500L)
        runCurrent()
    }

    @Test
    fun liveSeekWaitsForIdleAfterTheLatestRepeat() = runTest {
        val dispatches = mutableListOf<Long>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val fixture = fixture()
        val timeshift = fixture.presentation()
        val seek: suspend (TimeshiftContentTarget) -> TimeshiftContentSeekResult = { target -> fixture.seek(target) {
            dispatches += target.position.inWholeMilliseconds
            fixture.completed()
        } }
        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        runCurrent()
        advanceTimeBy(300L)
        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        assertEquals(480_000L, state.preview?.decision?.targetMs)
        advanceTimeBy(399L)
        runCurrent()
        assertTrue(dispatches.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(480_000L), dispatches)
    }

    @Test
    fun liveTimelineSameSourceSelectionKeepsSeekQueuedBehindInFlightDispatch() = runTest {
        val firstDispatch = CompletableDeferred<Unit>()
        val dispatches = mutableListOf<Long>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 5_400_000L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val fixture = fixture()
        val timeshift = fixture.presentation()
        val seek: suspend (TimeshiftContentTarget) -> TimeshiftContentSeekResult = { target -> fixture.seek(target) {
            dispatches += target.position.inWholeMilliseconds
            if (dispatches.size == 1) firstDispatch.await()
            fixture.completed()
        } }

        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        assertEquals(PlayerSeekPreviewPhase.PENDING, state.seekPreviewPhase(controlsVisible = false))
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf(510_000L), dispatches)
        assertEquals(
            PlayerSeekPreviewPhase.DISPATCHED,
            state.seekPreviewPhase(controlsVisible = false),
        )

        val pickAction = channelPickAction(ChannelId(7), ChannelId(7))
        assertEquals(ChannelPickAction.CLOSE_DRAWER, pickAction)
        if (pickAction == ChannelPickAction.TUNE) state.invalidateForSourceChange()
        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        assertEquals(480_000L, state.preview?.decision?.targetMs)
        firstDispatch.complete(Unit)
        runCurrent()
        advanceTimeBy(400L)
        runCurrent()

        assertEquals(listOf(510_000L, 480_000L), dispatches)
        assertEquals(480_000L, state.preview?.decision?.targetMs)
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
        val fixture = fixture()

        state.queueRelativeSeek(
            state = fixture.presentation(10_000L),
            requestedDeltaMs = -30_000L,
            unavailableText = "unavailable",
            clampedText = "clamped",
            expiredText = "expired", replacedText = "replaced", uncertainText = "uncertain",
            seekContent = { target -> fixture.seek(target) {
                dispatches += target.position.inWholeMilliseconds
                fixture.completed()
            } },
        )
        assertFalse(state.applyFeedback(oldFeedbackToken, "stale"))

        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf(0L), dispatches)
        assertEquals("clamped", state.feedback)

        advanceTimeBy(950L)
        runCurrent()
        assertEquals(PlayerSeekPreviewPhase.NONE, state.seekPreviewPhase(controlsVisible = false))
        assertEquals("clamped", state.feedback)
    }

    @Test
    fun unconfirmedSeekDoesNotProjectAnUnobservedServerPosition() = runTest {
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 5_400_000L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val fixture = fixture()
        val timeshift = fixture.presentation()

        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain") { target ->
            fixture.seek(target) { fixture.completed(TimeshiftCommandResult.TIMEOUT) }
        }
        advanceTimeBy(400L)
        runCurrent()

        assertEquals("uncertain", state.feedback)
        state.queueRelativeSeek(timeshift, -30_000L, "unavailable", "clamped", "expired", "replaced", "uncertain") { target ->
            fixture.seek(target) { fixture.completed() }
        }
        assertEquals(510_000L, state.preview?.decision?.targetMs)
    }

    @Test
    fun liveTimelineCancellationAndDisposalDoNotDispatchPendingSeek() = runTest {
        val dispatches = mutableListOf<Long>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        val fixture = fixture()
        val timeshift = fixture.presentation()

        state.queueRelativeSeek(
            timeshift,
            -30_000L,
            "unavailable",
            "clamped",
            "expired", "replaced", "uncertain",
        ) { target -> fixture.seek(target) {
            dispatches += target.position.inWholeMilliseconds
            fixture.completed()
        } }
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
            "expired", "replaced", "uncertain",
        ) { target -> fixture.seek(target) {
            dispatches += target.position.inWholeMilliseconds
            fixture.completed()
        } }
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
        val fixture = fixture()
        val timeshift = fixture.presentation()

        state.queueRelativeSeek(timeshift, -30_000L, "old unavailable", "old clamped", "expired", "replaced", "uncertain") { target -> fixture.seek(target) {
            commands += "old:${target.position.inWholeMilliseconds}"
            fixture.completed()
        } }
        state.invalidateForSourceChange()
        fixture.replaceSubscription()
        fixture.updateHistory(0.seconds, 600.seconds)
        state.queueRelativeSeek(fixture.presentation(), 30_000L, "new unavailable", "new clamped", "expired", "replaced", "uncertain") { target -> fixture.seek(target) {
            commands += "new:${target.position.inWholeMilliseconds}"
            fixture.completed()
        } }
        advanceTimeBy(400L)
        runCurrent()

        assertEquals(listOf("new:570000"), commands)
    }

    @Test
    fun rejectedInFlightSeekExplainsDiscardedStackedInput() = runTest {
        val fixture = fixture()
        val result = CompletableDeferred<TimeshiftContentSeekResult>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        var dispatches = 0
        val seek: suspend (TimeshiftContentTarget) -> TimeshiftContentSeekResult = {
            dispatches++
            result.await()
        }
        state.queueRelativeSeek(fixture.presentation(), -30_000L,
            "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        advanceTimeBy(400L)
        runCurrent()
        state.queueRelativeSeek(fixture.presentation(), -30_000L,
            "unavailable", "clamped", "expired", "replaced", "uncertain", seek)
        result.complete(TimeshiftContentSeekResult.Expired)
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(1, dispatches)
        assertEquals("expired", state.feedback)
        assertNull(state.preview)
        state.dispose()
    }

    @Test
    fun liveTimelineSourceInvalidationDetachesNewCommandFromSuspendedOldCommand() = runTest {
        val oldDispatchResult = CompletableDeferred<TimeshiftContentSeekResult.Completed>()
        val oldDispatchCancelled = CompletableDeferred<Unit>()
        val commands = mutableListOf<String>()
        val state = LiveTimelinePresentationState(
            scope = this,
            currentEpochMillis = { 0L },
            monotonicTimeMillis = { testScheduler.currentTime },
        )
        state.showFeedback("old feedback")
        val oldFixture = fixture()
        val newFixture = fixture()
        state.queueRelativeSeek(
            state = oldFixture.presentation(),
            requestedDeltaMs = -30_000L,
            unavailableText = "unavailable",
            clampedText = "clamped",
            expiredText = "expired", replacedText = "replaced", uncertainText = "uncertain",
            seekContent = { target -> oldFixture.seek(target) {
                commands += "old:${target.position.inWholeMilliseconds}"
                try {
                    oldDispatchResult.await()
                } catch (error: CancellationException) {
                    oldDispatchCancelled.complete(Unit)
                    throw error
                }
            } },
        )
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf("old:510000"), commands)

        state.invalidateForSourceChange()
        runCurrent()
        assertTrue(oldDispatchCancelled.isCompleted)
        assertNull(state.preview)
        state.queueRelativeSeek(
            state = newFixture.presentation(590_000L),
            requestedDeltaMs = 30_000L,
            unavailableText = "new unavailable",
            clampedText = "new clamped",
            expiredText = "expired", replacedText = "replaced", uncertainText = "uncertain",
            seekContent = { target -> newFixture.seek(target) {
                commands += "new:${target.position.inWholeMilliseconds}"
                newFixture.completed()
            } },
        )
        assertEquals(600_000L, state.preview?.decision?.targetMs)
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(listOf("old:510000", "new:600000"), commands)
        assertEquals("new clamped", state.feedback)

        oldDispatchResult.complete(oldFixture.completed(TimeshiftCommandResult.REJECTED))
        runCurrent()
        assertEquals(listOf("old:510000", "new:600000"), commands)
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
            currentCanSeek = { true },
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
            currentCanSeek = { true },
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
            currentCanSeek = { true },
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
