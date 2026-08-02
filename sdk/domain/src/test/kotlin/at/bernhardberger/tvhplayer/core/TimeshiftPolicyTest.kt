package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftPolicyTest {
    @Test
    fun capabilityRequiresObservedServerStatus() {
        assertFalse(
            timeshiftStateFromStatus(
                advertisedPeriodSec = 7_200,
                shiftMicros = null,
                startMicros = null,
                endMicros = null,
                full = false,
                speed = null,
                nowEpochMs = 1_700_000_000_000,
            ).available
        )
        assertTrue(
            timeshiftStateFromStatus(
                advertisedPeriodSec = 7_200,
                shiftMicros = 0,
                startMicros = 10_000_000,
                endMicros = 20_000_000,
                full = false,
                speed = 100,
                nowEpochMs = 1_700_000_000_000,
            ).available
        )
    }

    @Test
    fun statusMapsServerWindowPositionAndPause() {
        val state = timeshiftStateFromStatus(
            advertisedPeriodSec = 7_200,
            shiftMicros = 30_000_000,
            startMicros = 10_000_000,
            endMicros = 130_000_000,
            full = false,
            speed = 0,
            nowEpochMs = 1_700_000_000_000,
        )

        assertEquals(-120_000L, state.bufferStartMs)
        assertEquals(-30_000L, state.positionMs)
        assertEquals(0L, state.liveEdgeMs)
        assertEquals(10_000_000L, state.serverStartUs)
        assertEquals(130_000_000L, state.serverEndUs)
        assertTrue(state.paused)
    }

    @Test
    fun startupWindowUsesReportedExtentInsteadOfAdvertisedCapacity() {
        val state = timeshiftStateFromStatus(
            advertisedPeriodSec = 1_800,
            shiftMicros = 0,
            startMicros = 45_000_000,
            endMicros = 50_000_000,
            full = false,
            speed = 100,
            nowEpochMs = 1_700_000_000_000,
        )

        assertEquals(-5_000L, state.bufferStartMs)
        assertFalse(isTimeshiftActive(state))
    }

    @Test
    fun seekClampsToAdvancingServerWindowAndReportsClamp() {
        val state = TimeshiftState(
            available = true,
            bufferStartMs = -90_000,
            positionMs = -20_000,
            liveEdgeMs = 0,
            paused = false,
        )

        assertEquals(
            TimeshiftSeekDecision(targetMs = -50_000, deltaMs = -30_000, clamped = false),
            timeshiftSeek(state, deltaMs = -30_000),
        )
        assertEquals(
            TimeshiftSeekDecision(targetMs = -90_000, deltaMs = -70_000, clamped = true),
            timeshiftSeek(state, deltaMs = -300_000),
        )
        assertEquals(
            TimeshiftSeekDecision(targetMs = 0, deltaMs = 20_000, clamped = true),
            timeshiftSeek(state, deltaMs = 30_000),
        )
    }

    @Test
    fun rapidSeekPressesCoalesceIntoOneClampedServerDelta() {
        val state = TimeshiftState(
            available = true,
            bufferStartMs = -75_000,
            positionMs = 0,
            liveEdgeMs = 0,
        )

        var pendingDeltaMs = 0L
        repeat(3) {
            pendingDeltaMs = coalesceTimeshiftSeekDelta(
                state = state,
                pendingDeltaMs = pendingDeltaMs,
                requestedDeltaMs = -TIMESHIFT_SEEK_STEP_MS,
            )
        }

        assertEquals(-75_000L, pendingDeltaMs)
        assertEquals(
            TimeshiftSeekDecision(targetMs = -75_000, deltaMs = -75_000, clamped = false),
            timeshiftSeek(state, pendingDeltaMs),
        )
    }

    @Test
    fun dispatchQueueKeepsOneRequestInFlightAndPreservesQueuedOrder() {
        val atLive = TimeshiftState(
            available = true,
            bufferStartMs = -180_000L,
            positionMs = 0L,
            liveEdgeMs = 0L,
        )
        var queue = TimeshiftSeekQueueState()

        queue = queueTimeshiftSeek(queue, atLive, -30_000L)
        val first = requireNotNull(beginTimeshiftSeekDispatch(queue))
        queue = first.queue
        assertEquals(-30_000L, first.deltaMs)

        queue = queueTimeshiftSeek(queue, atLive, -30_000L)
        queue = queueTimeshiftSeek(queue, atLive, -30_000L)
        assertNull(beginTimeshiftSeekDispatch(queue))

        queue = completeTimeshiftSeekDispatch(
            queue,
            TimeshiftSeekDecision(-30_000L, -30_000L, clamped = false),
        )
        val second = requireNotNull(beginTimeshiftSeekDispatch(queue))
        assertEquals(-60_000L, second.deltaMs)
        assertTrue(second.queue.dispatchInFlight)
    }

    @Test
    fun queuedReversalIsRebasedFromTheCompletedDispatchTarget() {
        val atLive = TimeshiftState(
            available = true,
            bufferStartMs = -180_000L,
            positionMs = 0L,
            liveEdgeMs = 0L,
        )
        val first = requireNotNull(
            beginTimeshiftSeekDispatch(
                queueTimeshiftSeek(TimeshiftSeekQueueState(), atLive, -30_000L)
            )
        )
        var queue = queueTimeshiftSeek(first.queue, atLive, 30_000L)

        assertEquals(
            TimeshiftSeekDecision(0L, 30_000L, clamped = false),
            queuedTimeshiftSeekDecision(queue),
        )
        assertNull(beginTimeshiftSeekDispatch(queue))

        queue = completeTimeshiftSeekDispatch(
            queue,
            TimeshiftSeekDecision(-30_000L, -30_000L, clamped = false),
        )
        assertEquals(30_000L, requireNotNull(beginTimeshiftSeekDispatch(queue)).deltaMs)
    }

    @Test
    fun inputAfterCompletionUsesProjectedTargetUntilObservedStateCatchesUp() {
        val staleAtLive = TimeshiftState(
            available = true,
            bufferStartMs = -180_000L,
            positionMs = 0L,
            liveEdgeMs = 0L,
        )
        val first = requireNotNull(
            beginTimeshiftSeekDispatch(
                queueTimeshiftSeek(TimeshiftSeekQueueState(), staleAtLive, -30_000L)
            )
        )
        var completed = completeTimeshiftSeekDispatch(
            first.queue,
            TimeshiftSeekDecision(-30_000L, -30_000L, clamped = false),
        )

        completed = queueTimeshiftSeek(completed, staleAtLive, 30_000L)

        assertEquals(
            TimeshiftSeekDecision(0L, 30_000L, clamped = false),
            queuedTimeshiftSeekDecision(completed),
        )
        assertEquals(30_000L, requireNotNull(beginTimeshiftSeekDispatch(completed)).deltaMs)
    }

    @Test
    fun inputAfterCompletionCanCancelAnExistingQueuedReversalAgainstStaleState() {
        val staleAtLive = TimeshiftState(
            available = true,
            bufferStartMs = -180_000L,
            positionMs = 0L,
            liveEdgeMs = 0L,
        )
        val first = requireNotNull(
            beginTimeshiftSeekDispatch(
                queueTimeshiftSeek(TimeshiftSeekQueueState(), staleAtLive, -30_000L)
            )
        )
        var queue = queueTimeshiftSeek(first.queue, staleAtLive, 30_000L)
        queue = completeTimeshiftSeekDispatch(
            queue,
            TimeshiftSeekDecision(-30_000L, -30_000L, clamped = false),
        )

        queue = queueTimeshiftSeek(queue, staleAtLive, -30_000L)

        assertEquals(
            TimeshiftSeekDecision(-30_000L, 0L, clamped = false),
            queuedTimeshiftSeekDecision(queue),
        )
        assertNull(beginTimeshiftSeekDispatch(queue))
    }

    @Test
    fun cancellingPendingSeekDoesNotClaimToCancelAnActiveDispatch() {
        val state = TimeshiftState(
            available = true,
            bufferStartMs = -180_000L,
            positionMs = 0L,
            liveEdgeMs = 0L,
        )
        val active = requireNotNull(
            beginTimeshiftSeekDispatch(
                queueTimeshiftSeek(TimeshiftSeekQueueState(), state, -30_000L)
            )
        ).queue
        val activeWithPending = queueTimeshiftSeek(active, state, -30_000L)

        val cancelled = cancelPendingTimeshiftSeek(activeWithPending)

        assertEquals(0L, cancelled.pendingDeltaMs)
        assertTrue(cancelled.dispatchInFlight)
        assertNull(beginTimeshiftSeekDispatch(cancelled))
    }

    @Test
    fun seekControlsFollowTheAvailableBufferAroundTheCurrentPosition() {
        val atLive = TimeshiftState(
            available = true,
            bufferStartMs = -120_000,
            positionMs = 0,
            liveEdgeMs = 0,
        )
        assertTrue(canSeekTimeshiftBackward(atLive))
        assertFalse(canSeekTimeshiftForward(atLive))

        val normalLiveLatency = atLive.copy(positionMs = -4_000)
        assertFalse(canSeekTimeshiftForward(normalLiveLatency))

        val behindLive = atLive.copy(positionMs = -30_000)
        assertTrue(canSeekTimeshiftBackward(behindLive))
        assertTrue(canSeekTimeshiftForward(behindLive))

        val atBufferStart = atLive.copy(positionMs = -120_000)
        assertFalse(canSeekTimeshiftBackward(atBufferStart))
        assertTrue(canSeekTimeshiftForward(atBufferStart))

        assertFalse(canSeekTimeshiftBackward(TimeshiftState()))
        assertFalse(canSeekTimeshiftForward(TimeshiftState()))
    }

    @Test
    fun everyLiveEdgeConsumerUsesTheSameInclusiveTolerance() {
        val live = TimeshiftState(
            available = true,
            bufferStartMs = -120_000,
            positionMs = -TIMESHIFT_LIVE_EDGE_TOLERANCE_MS,
            liveEdgeMs = 0,
        )
        val livePresentation = timeshiftPositionPresentation(live)

        assertTrue(livePresentation.atLiveEdge)
        assertEquals(TIMESHIFT_LIVE_EDGE_TOLERANCE_MS, livePresentation.behindLiveMs)
        assertFalse(canSeekTimeshiftForward(live))
        assertFalse(isTimeshiftActive(live))

        val behindLive = live.copy(positionMs = -TIMESHIFT_LIVE_EDGE_TOLERANCE_MS - 1L)
        val behindPresentation = timeshiftPositionPresentation(behindLive)

        assertFalse(behindPresentation.atLiveEdge)
        assertEquals(
            TIMESHIFT_LIVE_EDGE_TOLERANCE_MS + 1L,
            behindPresentation.behindLiveMs,
        )
        assertTrue(canSeekTimeshiftForward(behindLive))
        assertTrue(isTimeshiftActive(behindLive))
    }

    @Test
    fun activeTimeshiftRequiresPauseOrARealDelayFromLive() {
        val live = TimeshiftState(available = true, bufferStartMs = -60_000)
        assertFalse(isTimeshiftActive(live))
        assertTrue(isTimeshiftActive(live.copy(paused = true)))
        assertFalse(isTimeshiftActive(live.copy(positionMs = -4_000)))
        assertTrue(isTimeshiftActive(live.copy(positionMs = -6_000)))
    }

    @Test
    fun absoluteSeekTargetUsesTheReportedLiveEdgeTimestamp() {
        val state = TimeshiftState(
            available = true,
            bufferStartMs = -120_000,
            positionMs = 0,
            serverStartUs = 10_000_000,
            serverEndUs = 130_000_000,
        )
        val decision = timeshiftSeek(state, -30_000)

        assertEquals(100_000_000L, timeshiftAbsoluteTargetUs(state, decision))
        assertEquals(null, timeshiftAbsoluteTargetUs(state.copy(serverEndUs = null), decision))
    }
}
