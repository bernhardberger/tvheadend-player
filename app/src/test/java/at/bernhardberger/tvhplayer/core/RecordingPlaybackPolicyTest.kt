package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent
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
                path = "/dvrfile/1",
                size = 500L,
                growing = false,
            ),
            recordingPlaybackAvailability(completed),
        )
        assertEquals(
            RecordingPlaybackAvailability.Ready(
                path = "/dvrfile/1",
                size = null,
                growing = true,
            ),
            recordingPlaybackAvailability(active),
        )
        assertEquals(
            RecordingPlaybackAvailability.Ready(
                path = "/dvrfile/1",
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

    @Test
    fun rapidSeekStepsStackFromThePendingTarget() {
        assertEquals(
            80_000L,
            recordingStackedSeekTarget(
                currentMs = 10_000L,
                pendingTargetMs = 50_000L,
                durationMs = 120_000L,
                deltaMs = 30_000L,
            ),
        )
        assertEquals(
            40_000L,
            recordingStackedSeekTarget(
                currentMs = 10_000L,
                pendingTargetMs = null,
                durationMs = 120_000L,
                deltaMs = 30_000L,
            ),
        )
    }

    @Test
    fun seekFeedbackWaitsForPlaybackToResumeOrPausedMediaToBecomeReady() {
        assertEquals(
            false,
            recordingSeekFeedbackSettled(
                playerReady = false,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            false,
            recordingSeekFeedbackSettled(
                playerReady = true,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = true,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = true,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = true,
                playerEnded = false,
                playWhenReady = false,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = false,
                playerEnded = true,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = false,
            ),
        )
        assertEquals(
            true,
            recordingSeekFeedbackSettled(
                playerReady = false,
                playerEnded = false,
                playWhenReady = true,
                isPlaying = false,
                playbackFailed = true,
            ),
        )
    }

    @Test
    fun hiddenPlayerUsesControlsAndSeekContract() {
        assertEquals(
            RecordingPlaybackKeyAction.SEEK_BACK,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            RecordingPlaybackKeyAction.SEEK_FORWARD,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals(
            RecordingPlaybackKeyAction.REVEAL_CONTROLS,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_UP),
        )
        assertEquals(
            RecordingPlaybackKeyAction.REVEAL_CONTROLS,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_DOWN),
        )
        assertEquals(
            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(
            RecordingPlaybackKeyAction.HIDE_CONTROLS,
            recordingPlaybackKeyAction(true, KeyEvent.KEYCODE_BACK),
        )
        assertEquals(
            RecordingPlaybackKeyAction.CLOSE,
            recordingPlaybackKeyAction(false, KeyEvent.KEYCODE_BACK),
        )
    }

    @Test
    fun visiblePlayerLeavesDirectionKeysToControlFocus() {
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
        ).forEach { keyCode ->
            assertEquals(
                RecordingPlaybackKeyAction.PASS_THROUGH,
                recordingPlaybackKeyAction(true, keyCode),
            )
        }
    }

    @Test
    fun revealingKeyCycleIsSuppressedUntilItsMatchingKeyUp() {
        assertTrue(
            recordingPlaybackSuppressesRevealingKey(
                revealingKeyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            )
        )
        assertEquals(
            false,
            recordingPlaybackSuppressesRevealingKey(
                revealingKeyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
            ),
        )
        assertEquals(
            false,
            recordingPlaybackSuppressesRevealingKey(
                revealingKeyCode = null,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            ),
        )
    }

    @Test
    fun recordingStartsCompleteCyclesForEveryFocusCreatingAction() {
        listOf(
            RecordingPlaybackKeyAction.REVEAL_CONTROLS,
            RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            RecordingPlaybackKeyAction.OPEN_INFO,
        ).forEach { action ->
            assertTrue(action.name, recordingKeyActionStartsOpeningCycle(action))
        }

        listOf(
            RecordingPlaybackKeyAction.PASS_THROUGH,
            RecordingPlaybackKeyAction.SEEK_BACK,
            RecordingPlaybackKeyAction.SEEK_FORWARD,
            RecordingPlaybackKeyAction.HIDE_CONTROLS,
            RecordingPlaybackKeyAction.CLOSE,
        ).forEach { action ->
            assertEquals(false, recordingKeyActionStartsOpeningCycle(action))
        }
    }

    @Test
    fun finishedRecordingAlwaysStopsAndOnlyClosesAVisiblePlayer() {
        assertEquals(
            RecordingFinishedAction.STOP_AND_CLOSE_PLAYER,
            recordingFinishedAction(
                recordingFinished = true,
                activeRecordingId = 7,
                recordingPlayerVisible = true,
            ),
        )
        assertEquals(
            RecordingFinishedAction.STOP,
            recordingFinishedAction(
                recordingFinished = true,
                activeRecordingId = 7,
                recordingPlayerVisible = false,
            ),
        )
        assertEquals(
            RecordingFinishedAction.NONE,
            recordingFinishedAction(
                recordingFinished = false,
                activeRecordingId = 7,
                recordingPlayerVisible = false,
            ),
        )
        assertEquals(
            RecordingFinishedAction.NONE,
            recordingFinishedAction(
                recordingFinished = true,
                activeRecordingId = null,
                recordingPlayerVisible = false,
            ),
        )
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
