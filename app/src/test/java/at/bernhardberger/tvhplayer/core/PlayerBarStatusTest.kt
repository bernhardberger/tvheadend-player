package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.ui.player.PlayerChromeTimeline
import at.bernhardberger.tvhplayer.ui.player.stepDeltaMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBarStatusTest {
    /** The end and the distance as the chrome draws them for live TV: `end   −3:23`; null parts are absent. */
    private fun row(end: PlayerBarEnd?) = end?.let { listOfNotNull(it.end, it.behindText).joinToString("   ") }

    @Test fun liveWithGuideReadsTheProgrammeEndAndTheDistanceOnlyBehindLive() {
        assertEquals("21:45", row(liveBarEnd(0L, "21:45")))
        assertEquals("21:45", row(liveBarEnd(TIMESHIFT_LIVE_EDGE_TOLERANCE_MS, "21:45")))
        assertTrue("the live edge is a state, not a text", liveBarEnd(0L, "21:45")!!.atLive)
        assertEquals("21:45   −3:23", row(liveBarEnd(203_000L, "21:45")))
        assertEquals("21:45   −1:02:10", row(liveBarEnd(3_730_000L, "21:45")))
        assertEquals("the end and the distance are separate parts", "21:45", liveBarEnd(203_000L, "21:45")!!.end)
        assertEquals("−3:23", liveBarEnd(203_000L, "21:45")!!.behindText)
    }

    @Test fun liveWithoutGuideButWithTimeshiftReadsOnlyTheDistance() {
        assertEquals("", row(liveBarEnd(0L, null)))
        assertEquals("−3:23", row(liveBarEnd(203_000L, null)))
        assertEquals("−1:30", row(liveBarEnd(90_000L, null)))
    }

    @Test fun liveWithoutTimeshiftIsAlwaysAtTheEdge() {
        // The screen passes zero behind without timeshift.
        assertEquals("21:45", row(liveBarEnd(0L, "21:45")))
        assertTrue(liveBarEnd(0L, "21:45")!!.atLive)
        assertTrue(liveBarEnd(0L, null)!!.atLive)
        assertNull(liveBarEnd(0L, null)!!.behindText)
    }

    @Test fun anUnknownDistanceRetainsALiveEndWithoutClaimingLiveEvenWithoutAClock() {
        // The chrome holds the last row; a first unknown row shows no distance.
        assertEquals("21:45", row(liveBarEnd(null, "21:45")))
        assertNull(liveBarEnd(null, "21:45")!!.behindText)
        assertTrue(liveBarEnd(null, null)!!.liveEnd)
        assertFalse(liveBarEnd(null, null)!!.atLive)
        assertNull(liveBarEnd(null, null)!!.announcement)
        assertFalse(liveBarEnd(null, "21:45")!!.hasDistance)
    }

    /** A recording's length and, for the chip over hidden chrome, its distance: `length   −55:05`. */
    private fun chip(end: PlayerBarEnd?) = end?.let { listOfNotNull(it.end, it.distanceText("Live")).joinToString("   ") }

    @Test fun recordingReadsTheLengthAndKeepsTheRemainingTimeForTheHiddenChip() {
        assertEquals("1:12:30   −55:05", chip(recordingBarEnd(positionMs = 1_045_000L, durationMs = 4_350_000L, growing = false)))
        assertEquals("1:00   −0:00", chip(recordingBarEnd(90_000L, 60_000L, growing = false)))
        assertEquals("4:00:00   −3:57:34", chip(recordingBarEnd(60_000L, 60_000L + 14_254_000L, growing = false, lengthMs = 14_400_000L)))
        assertFalse(recordingBarEnd(1_000L, 60_000L, growing = false)!!.liveEnd)
    }

    @Test fun aGrowingRecordingReadsItsCurrentLengthAndKeepsTheDistanceToItsHeadForTheHiddenChip() {
        val behind = recordingBarEnd(positionMs = 1_045_000L, durationMs = 4_350_000L, growing = true)!!
        assertEquals("1:12:30   −55:05", chip(behind))
        assertEquals("the end is the length alone, without a mark", "1:12:30", behind.end)
        assertTrue("its head is a live end", behind.liveEnd)
        val atHead = recordingBarEnd(positionMs = 58_000L, durationMs = 60_000L, growing = true)!!
        assertEquals("1:00   Live", chip(atHead))
        assertTrue(atHead.liveEnd)
    }

    @Test fun aRecordingOfUnknownLengthHasNoEndAndNoDistance() {
        assertNull(recordingBarEnd(5_000L, durationMs = null, growing = false))
        assertNull(recordingBarEnd(5_000L, durationMs = null, growing = true))
        assertNull(recordingBarEnd(5_000L, durationMs = 0L, growing = false))
    }

    @Test fun theTickingDistanceTravelsApartFromTheEnd() {
        val end = liveBarEnd(203_000L, "21:45")!!
        assertEquals("21:45", end.withoutDistance().end)
        assertNull(end.withoutDistance().distanceText("Live"))
        assertEquals(end, end.withoutDistance().withDistance(end.distance))
    }

    @Test fun theStateCellShowsTheRealState() {
        assertEquals(PlayerStateCell.PLAYING, playerStateCell(paused = false))
        assertEquals(PlayerStateCell.PAUSED, playerStateCell(paused = true))
        assertEquals("busy is not a chrome state", listOf(PlayerStateCell.PLAYING, PlayerStateCell.PAUSED), PlayerStateCell.entries)
    }

    @Test fun theHiddenChipShowsOnlyThePausedDistance() {
        val behind = liveBarEnd(203_000L, "21:45")
        assertEquals("−3:23", hiddenStatusText(PlayerStateCell.PAUSED, behind, "Live"))
        assertEquals("Live", hiddenStatusText(PlayerStateCell.PAUSED, liveBarEnd(0L, "21:45"), "Live"))
        assertEquals("−55:05", hiddenStatusText(PlayerStateCell.PAUSED, recordingBarEnd(1_045_000L, 4_350_000L, growing = false), "Live"))
        // Nothing but the glyph without a distance (timing unknown, unknown recording length).
        assertNull(hiddenStatusText(PlayerStateCell.PAUSED, liveBarEnd(null, "21:45"), "Live"))
        assertNull(hiddenStatusText(PlayerStateCell.PAUSED, null, "Live"))
        assertNull(hiddenStatusText(PlayerStateCell.PLAYING, behind, "Live"))
    }

    @Test fun announcementsNameStatesNotDistances() {
        assertEquals(PlayerEndAnnouncement.LIVE, liveBarEnd(0L, "21:45")?.announcement)
        assertEquals(PlayerEndAnnouncement.BEHIND_LIVE, liveBarEnd(60_000L, "21:45")?.announcement)
        assertEquals(liveBarEnd(60_000L, null)?.announcement, liveBarEnd(61_000L, null)?.announcement)
        assertNull("tuning has no state to announce", liveBarEnd(null, "21:45")?.announcement)
        assertNull("a finished recording has no live edge", recordingBarEnd(0L, 60_000L, growing = false)?.announcement)
        assertEquals(PlayerEndAnnouncement.BEHIND_LIVE, recordingBarEnd(0L, 60_000L, growing = true)?.announcement)
        assertEquals(PlayerEndAnnouncement.LIVE, recordingBarEnd(59_000L, 60_000L, growing = true)?.announcement)
    }

    @Test fun theStepReadoutShowsTheTargetsDistanceOrLiveAtTheEdge() {
        assertEquals(StepReadout("−3:53"), liveStepReadout(233_000L, "Live"))
        assertEquals(StepReadout("Live", atLive = true), liveStepReadout(0L, "Live"))
        assertEquals(StepReadout("Live", atLive = true), liveStepReadout(TIMESHIFT_LIVE_EDGE_TOLERANCE_MS, "Live"))
        assertEquals(StepReadout("−0:06"), liveStepReadout(TIMESHIFT_LIVE_EDGE_TOLERANCE_MS + 1_000L, "Live"))
    }

    @Test fun aRecordingsStepReadoutShowsTheTargetPositionAndLiveOnlyAtAGrowingHead() {
        assertEquals(StepReadout("0:16:55"), recordingStepReadout(1_015_000L, 4_350_000L, growing = false, liveLabel = "Live"))
        assertEquals(StepReadout("0:16:55"), recordingStepReadout(1_015_000L, 4_350_000L, growing = true, liveLabel = "Live"))
        // A finished recording's end is not live.
        assertEquals(StepReadout("1:12:30"), recordingStepReadout(4_350_000L, 4_350_000L, growing = false, liveLabel = "Live"))
        assertEquals(StepReadout("Live", atLive = true), recordingStepReadout(4_350_000L, 4_350_000L, growing = true, liveLabel = "Live"))
        assertEquals(StepReadout("Live", atLive = true), recordingStepReadout(4_346_000L, 4_350_000L, growing = true, liveLabel = "Live"))
    }

    @Test fun theElapsedLabelPadsToTheLengthsHourFormat() {
        assertEquals("0:10:00", recordingElapsedLabel(600_000L, 3_600_000L))
        assertEquals("10:00", recordingElapsedLabel(600_000L, 1_800_000L))
        assertEquals("10:00", recordingElapsedLabel(600_000L, null))
        assertEquals("1:10:00", recordingElapsedLabel(4_200_000L, 4_350_000L))
    }

    @Test fun theBufferStartClockIsTheLiveEdgeMinusTheBuffer() {
        // Ninety seconds of history behind a live edge at 1 000 s.
        assertEquals(910L, bufferStartClockSec(nowSec = 1_000L, liveEdgeMs = 0L, bufferStartMs = -90_000L))
        assertEquals("the buffer origin does not matter", 910L, bufferStartClockSec(1_000L, 500_000L, 410_000L))
        assertEquals("no history", 1_000L, bufferStartClockSec(1_000L, 0L, 0L))
        // The ticking clock and the growing live edge move together, so the buffer start holds.
        assertEquals(bufferStartClockSec(1_000L, 0L, -90_000L), bufferStartClockSec(1_004L, 4_000L, -90_000L))
    }

    @Test fun stepDirectionFollowsTheSignOfTheStepsNetMovement() {
        assertEquals(StepDirection.BEHIND, stepDirection(-30_000L))
        assertEquals(StepDirection.AHEAD, stepDirection(30_000L))
        assertNull(stepDirection(0L))
    }

    @Test fun stepDirectionStaysBehindWhenTheStepIsClampedAtTheBufferStart() {
        // Stepping back from 10 s above the buffer start displaces only 10 s, still backwards; the
        // start rolling forward or the seek landing does not turn it into a forward step.
        val decision = TimeshiftSeekDecision(targetMs = -50_000L, deltaMs = -10_000L, clamped = true)
        assertEquals(StepDirection.BEHIND, stepDirection(decision.deltaMs))
    }

    @Test fun stepDirectionIgnoresThePlaybackPositionOvertakingTheTargetWhileLanding() {
        val timeline = PlayerChromeTimeline.Recording(positionMs = 100_000L, durationMs = 600_000L, targetMs = 70_000L, originMs = 100_000L)
        assertEquals(-30_000L, timeline.stepDeltaMs)
        // The seek lands and playback moves past the target: the step is still backwards.
        for (position in listOf(100_000L, 70_000L, 70_500L, 72_000L, 100_500L, 200_000L)) {
            assertEquals(StepDirection.BEHIND, stepDirection(requireNotNull(timeline.copy(positionMs = position).stepDeltaMs)))
        }
        val forward = timeline.copy(targetMs = 130_000L)
        for (position in listOf(100_000L, 130_000L, 131_000L, 90_000L)) {
            assertEquals(StepDirection.AHEAD, stepDirection(requireNotNull(forward.copy(positionMs = position).stepDeltaMs)))
        }
        assertNull(timeline.copy(targetMs = 100_000L).stepDeltaMs?.let(::stepDirection))
        assertNull(timeline.copy(targetMs = null).stepDeltaMs)
    }

    @Test fun recordingEndsAtNowPlusTheRemainingPlayback() {
        assertEquals(1_000L + 3_600L, recordingEndsAtSec(nowSec = 1_000L, positionMs = 600_000L, durationMs = 4_200_000L))
        // Paused, the end moves on with the clock.
        assertEquals(1_060L + 3_600L, recordingEndsAtSec(nowSec = 1_060L, positionMs = 600_000L, durationMs = 4_200_000L))
        assertEquals(1_001L, recordingEndsAtSec(nowSec = 1_000L, positionMs = 0L, durationMs = 500L))
        assertNull(recordingEndsAtSec(nowSec = 1_000L, positionMs = 0L, durationMs = null))
    }
}
