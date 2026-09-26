package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingRecoverySeekPolicyTest {
    private val recording = DvrEntryId(1)
    private val twentyMinutes = RecordingRecoveryPosition(recording, positionMs = 1_200_000, durationMs = 3_600_000)

    @Test fun aCompletedRecordingContinuesWhereItStood() {
        assertEquals(1_200_000L, recordingRecoverySeekMs(twentyMinutes, recording, completedRecording = true))
        assertEquals(1_200_000L, recordingRecoverySeekMs(twentyMinutes.copy(durationMs = null), recording, completedRecording = true))
    }

    @Test fun aGrowingRecordingContinuesWhereItStood() {
        assertEquals(1_200_000L, recordingRecoverySeekMs(twentyMinutes, recording, completedRecording = false, growingRecording = true))
        // The extent at the interruption is no final duration: a position near its end carries over.
        assertEquals(3_599_000L, recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 3_599_000), recording, completedRecording = false, growingRecording = true))
        assertNull(recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 0), recording, completedRecording = false, growingRecording = true))
        assertNull(recordingRecoverySeekMs(twentyMinutes, DvrEntryId(2), completedRecording = false, growingRecording = true))
    }

    @Test fun aRecordingNeitherCompletedNorGrowingKeepsTheViewersStart() {
        assertNull(recordingRecoverySeekMs(twentyMinutes, recording, completedRecording = false))
    }

    @Test fun aCompletedSeekWaitsForASeekableDurationAndDropsAPositionPastTheEnd() {
        assertEquals(RecordingRecoverySeek.Wait, completedSeek(durationMs = null, seekable = true))
        assertEquals(RecordingRecoverySeek.Wait, completedSeek(durationMs = 3_600_000, seekable = false))
        assertEquals(RecordingRecoverySeek.SeekTo(1_200_000), completedSeek(durationMs = 3_600_000, seekable = true))
        assertEquals(RecordingRecoverySeek.Drop, completedSeek(durationMs = 1_200_000, seekable = true))
        // Completed recovery has no settle bound: late seekability still continues there.
        assertEquals(RecordingRecoverySeek.SeekTo(1_200_000), completedSeek(durationMs = 3_600_000, seekable = true, pendingForMs = 120_000))
    }

    @Test fun aGrowingSeekWaitsUntilTheTimelineTurnsSeekable() {
        assertEquals(RecordingRecoverySeek.Wait, growingSeek(durationMs = null, seekable = false))
        assertEquals(RecordingRecoverySeek.Wait, growingSeek(durationMs = 1_800_000, seekable = false))
        // An extent not yet covering the edge margin waits for the next probe refresh.
        assertEquals(RecordingRecoverySeek.Wait, growingSeek(durationMs = 3_000, seekable = true))
        assertEquals(RecordingRecoverySeek.SeekTo(1_200_000), growingSeek(durationMs = 1_800_000, seekable = true))
    }

    @Test fun aGrowingSeekClampsToThePlayableEndOfTheExtent() {
        // The recording had grown past the interrupted position; the new probe reports less.
        assertEquals(RecordingRecoverySeek.SeekTo(897_000), growingSeek(durationMs = 900_000, seekable = true))
        assertEquals(RecordingRecoverySeek.SeekTo(1_197_000), growingSeek(durationMs = 1_200_000, seekable = true))
        assertEquals(RecordingRecoverySeek.SeekTo(1_200_000), growingSeek(durationMs = 1_203_000, seekable = true))
    }

    @Test fun aGrowingTimelineThatStaysUnseekableKeepsPlayingFromTheStart() {
        assertEquals(RecordingRecoverySeek.Wait, growingSeek(durationMs = 1_800_000, seekable = false, pendingForMs = 19_999))
        assertEquals(RecordingRecoverySeek.Drop, growingSeek(durationMs = 1_800_000, seekable = false, pendingForMs = 20_000))
        // Seekability after the bound does not move playback that began at the start.
        assertEquals(RecordingRecoverySeek.Drop, growingSeek(durationMs = 1_800_000, seekable = true, pendingForMs = 25_000))
    }

    @Test fun aLostGrowingPlayIsJudgedByTheInstalledCompletedTargetsDuration() {
        // A lost growing target carried no duration: admission keeps its position.
        assertEquals(3_480_000L, recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 3_480_000, durationMs = null),
            recording, completedRecording = true))
        // The installed completed target's own duration then judges the orderly finish.
        assertEquals(RecordingRecoverySeek.Wait, lostGrowingSeek(3_480_000, durationMs = null))
        assertEquals(RecordingRecoverySeek.Drop, lostGrowingSeek(3_420_000, durationMs = 3_600_000))
        assertEquals(RecordingRecoverySeek.SeekTo(3_419_999), lostGrowingSeek(3_419_999, durationMs = 3_600_000))
        assertEquals(RecordingRecoverySeek.SeekTo(2_940_000), lostGrowingSeek(2_940_000, durationMs = 3_600_000))
    }

    @Test fun aCarriedDurationLeavesTheInstalledTargetsSeekUnjudged() {
        // Admission judged it by the lost target's duration; the seek only drops past the end.
        assertEquals(RecordingRecoverySeek.SeekTo(3_420_000), recordingRecoverySeek(3_420_000,
            growingRecording = false, durationMs = 3_600_000, seekable = true, pendingForMs = 0))
    }

    @Test fun aGrowingTargetHasNoOrderlyCompletionCutoff() {
        assertEquals(RecordingRecoverySeek.SeekTo(3_420_000), recordingRecoverySeek(3_420_000,
            growingRecording = true, durationMs = 3_600_000, seekable = true, pendingForMs = 0,
            judgeOrderlyCompletion = true))
    }

    private fun lostGrowingSeek(positionMs: Long, durationMs: Long?) = recordingRecoverySeek(positionMs,
        growingRecording = false, durationMs, seekable = true, pendingForMs = 0, judgeOrderlyCompletion = true)

    private fun completedSeek(durationMs: Long?, seekable: Boolean, pendingForMs: Long = 0) =
        recordingRecoverySeek(1_200_000, growingRecording = false, durationMs, seekable, pendingForMs)

    private fun growingSeek(durationMs: Long?, seekable: Boolean, pendingForMs: Long = 0) =
        recordingRecoverySeek(1_200_000, growingRecording = true, durationMs, seekable, pendingForMs)

    @Test fun onlyTheSameRecordingCarriesItsPosition() {
        assertNull(recordingRecoverySeekMs(twentyMinutes, DvrEntryId(2), completedRecording = true))
        assertNull(recordingRecoverySeekMs(null, recording, completedRecording = true))
    }

    @Test fun theStartOrAFinishedPositionKeepsTheViewersStart() {
        assertNull(recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 0), recording, completedRecording = true))
        // The SDK's default orderly-completion fraction (0.95) marks the finished point.
        assertNull(recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 3_420_000), recording, completedRecording = true))
        assertEquals(3_419_999L, recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 3_419_999), recording, completedRecording = true))
    }
}
