package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTimelineProgressTest {
    @Test fun serverLivePinsLatencyToTheBufferOrProgrammeLiveEdgeOnlyWhilePlaying() {
        val state = AppTimeshiftState(available = true, positionMs = 2_000, liveEdgeMs = 10_000,
            serverBehindLiveMs = 0)
        val position = timeshiftPositionPresentation(state)
        for (edge in listOf(1f, 0.75f)) {
            assertEquals(edge, liveTimelineProgress(0.2f, edge, position, false, false))
            assertEquals(0.2f, liveTimelineProgress(0.2f, edge, position, true, false))
            assertEquals(0.2f, liveTimelineProgress(0.2f, edge, position, false, true))
        }
    }

    @Test fun absentServerShiftUsesFiveSecondToleranceAndUnknownTimingNeverPins() {
        for ((behind, expected) in listOf(5_000L to 1f, 5_001L to 0.2f)) {
            val state = AppTimeshiftState(available = true, positionMs = -behind)
            assertEquals(expected, liveTimelineProgress(0.2f, 1f, timeshiftPositionPresentation(state), false, false))
            assertEquals(0.2f, liveTimelineProgress(0.2f, 1f,
                timeshiftPositionPresentation(state.copy(timingKnown = false)), false, false))
        }
        assertEquals(0.2f, liveTimelineProgress(0.2f, 1f, null, false, false))
    }

    @Test fun pauseAndPreviewUseRawDisplayCoordinatesEvenWithinTheLiveTolerance() {
        val state = AppTimeshiftState(available = true, bufferStartMs = 0, positionMs = 8_000,
            liveEdgeMs = 10_000, displayLiveEdgeMs = 20_000, serverBehindLiveMs = 0)
        val range = timeshiftSeekbarRange(state)
        val position = timeshiftPositionPresentation(state)
        assertEquals(1f, liveTimelineProgress(range, position, false, false))
        assertEquals(0.4f, liveTimelineProgress(range, position, true, false))
        assertEquals(0.4f, liveTimelineProgress(range, position, false, true))
        // A server-reported non-live state also wins over a nearly-live sampled position.
        assertEquals(0.4f, liveTimelineProgress(range,
            timeshiftPositionPresentation(state.copy(serverBehindLiveMs = 8_000)), false, false))
    }
}
