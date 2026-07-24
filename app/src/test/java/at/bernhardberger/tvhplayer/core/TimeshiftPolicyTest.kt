package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                full = false,
                speed = null,
                nowEpochMs = 1_700_000_000_000,
            ).available
        )
        assertTrue(
            timeshiftStateFromStatus(
                advertisedPeriodSec = 7_200,
                shiftMicros = 0,
                startMicros = 1_699_999_990_000_000,
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
            startMicros = 1_699_999_880_000_000,
            full = false,
            speed = 0,
            nowEpochMs = 1_700_000_000_000,
        )

        assertEquals(-120_000L, state.bufferStartMs)
        assertEquals(-30_000L, state.positionMs)
        assertEquals(0L, state.liveEdgeMs)
        assertTrue(state.paused)
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
}
