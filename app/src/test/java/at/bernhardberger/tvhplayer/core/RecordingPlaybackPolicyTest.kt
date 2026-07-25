package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrFile
import at.bernhardberger.tvhplayer.htsp.DvrState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPlaybackPolicyTest {
    @Test
    fun completedGrowingAndFailedFilesArePlayableWhenServerExposesAPath() {
        val completed = recording(DvrState.COMPLETED, "/recordings/news.ts", 500L)
        val active = recording(DvrState.RECORDING, "/recordings/live.ts", null)
        val failed = recording(DvrState.FAILED, "/recordings/partial.ts", 250L)

        assertEquals(
            RecordingPlaybackAvailability.Ready(
                path = "/recordings/news.ts",
                size = 500L,
                growing = false,
            ),
            recordingPlaybackAvailability(completed),
        )
        assertEquals(
            RecordingPlaybackAvailability.Ready(
                path = "/recordings/live.ts",
                size = null,
                growing = true,
            ),
            recordingPlaybackAvailability(active),
        )
        assertEquals(
            RecordingPlaybackAvailability.Ready(
                path = "/recordings/partial.ts",
                size = 250L,
                growing = false,
            ),
            recordingPlaybackAvailability(failed),
        )
    }

    @Test
    fun unsupportedStatesAndMissingServerFilesExplainWhyPlaybackCannotStart() {
        assertEquals(
            RecordingPlaybackAvailability.NotReady,
            recordingPlaybackAvailability(recording(DvrState.SCHEDULED, "/future.ts", null)),
        )
        assertEquals(
            RecordingPlaybackAvailability.FileUnavailable,
            recordingPlaybackAvailability(recording(DvrState.COMPLETED, null, null)),
        )
    }

    @Test
    fun boundedReadsNeverPassKnownEndOfFile() {
        assertEquals(4, recordingReadLength(requested = 64, bytesRemaining = 4))
        assertEquals(64, recordingReadLength(requested = 64, bytesRemaining = null))
        assertTrue(recordingReadLength(requested = 64, bytesRemaining = 0) == 0)
    }

    @Test
    fun fixedSeekStepClampsAtRecordingBounds() {
        assertEquals(0L, recordingSeekTarget(10_000L, 120_000L, -30_000L))
        assertEquals(120_000L, recordingSeekTarget(110_000L, 120_000L, 30_000L))
        assertEquals(70_000L, recordingSeekTarget(40_000L, null, 30_000L))
    }

    private fun recording(state: DvrState, path: String?, size: Long?) = DvrEntry(
        id = 1,
        eventId = 2,
        channelId = 3,
        start = 100,
        stop = 200,
        title = "News",
        state = state,
        files = if (path == null) emptyList() else listOf(DvrFile(path = path, size = size)),
    )
}
