package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekbarPolicyTest {
    @Test
    fun recordingDomainIsZeroToDuration() {
        val range = recordingSeekbarRange(positionMs = 45_000, durationMs = 120_000)
        assertEquals(SeekbarDomain.RECORDING, range.domain)
        assertEquals(0L, range.startMs)
        assertEquals(120_000L, range.endMs)
        assertEquals(45_000L, range.positionMs)
        assertEquals(0.375f, range.progress, 0.001f)
    }

    @Test
    fun timeshiftDomainIsBufferToLiveEdge() {
        val range = timeshiftSeekbarRange(
            TimeshiftState(
                available = true,
                bufferStartMs = -600_000,
                positionMs = -120_000,
                liveEdgeMs = 0,
            ),
        )
        assertEquals(SeekbarDomain.TIMESHIFT, range.domain)
        assertEquals(-600_000L, range.startMs)
        assertEquals(0L, range.endMs)
        assertEquals(-120_000L, range.positionMs)
    }

    @Test
    fun timeshiftProgrammeBoundariesMapIntoSeekableRange() {
        val state = TimeshiftState(
            available = true,
            bufferStartMs = -600_000,
            positionMs = -120_000,
            liveEdgeMs = 0,
        )
        assertEquals(
            listOf(0.5f, 0.9f),
            timeshiftEpgBoundaryFractions(
                state = state,
                nowEpochSec = 1_000L,
                boundaryEpochSec = listOf(700L, 940L, 1_100L),
            ),
        )
    }

    @Test
    fun repeatAccelerationGrowsFromThirtySecondsToFiveMinutes() {
        assertEquals(30_000L, seekStepMs(0))
        assertEquals(30_000L, seekStepMs(3))
        assertEquals(120_000L, seekStepMs(4))
        assertEquals(120_000L, seekStepMs(11))
        assertEquals(300_000L, seekStepMs(12))
    }

    @Test
    fun scrubStaysInsideRange() {
        val range = recordingSeekbarRange(positionMs = 10_000, durationMs = 60_000)
        assertEquals(0L, seekbarScrub(range, direction = -1, repeatCount = 0))
        assertEquals(40_000L, seekbarScrub(range, direction = 1, repeatCount = 0))
        val nearEnd = recordingSeekbarRange(positionMs = 55_000, durationMs = 60_000)
        assertEquals(60_000L, seekbarScrub(nearEnd, direction = 1, repeatCount = 0))
    }

    @Test
    fun programmeProgressHiddenWithoutCurrentEvent() {
        assertTrue(shouldShowProgrammeProgress(hasCurrentEpgEvent = true))
        assertFalse(shouldShowProgrammeProgress(hasCurrentEpgEvent = false))
    }
}
