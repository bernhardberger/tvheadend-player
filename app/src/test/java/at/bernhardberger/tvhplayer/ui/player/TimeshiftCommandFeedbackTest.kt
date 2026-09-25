package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftCommandFeedbackTest {
    @Test
    fun hiddenControlsOkAndEnterResumeExactlyOnce() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            val action = playerKeyAction(PlayerKeyContext(PlayerSurface.LIVE, false, false, true), key)
            assertEquals(PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE, action)
            val calls = mutableListOf<String>()
            if (action == PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE) {
                dispatchTimeshiftPlaybackAction(MediaPlaybackAction.TOGGLE, false,
                    dispatch = { resume, rollback -> calls += "$resume/$rollback" })
            }
            assertEquals(listOf("true/false"), calls)
        }
    }

    @Test
    fun pauseAndToggleDispatchOneAtomicRuntimeOperationWithoutUiRollback() {
        for (action in listOf(MediaPlaybackAction.PAUSE, MediaPlaybackAction.TOGGLE)) {
            val calls = mutableListOf<String>()
            dispatchTimeshiftPlaybackAction(action, true,
                dispatch = { resume, rollback -> calls += "$resume/$rollback" })
            assertEquals(listOf("false/null"), calls)
        }
    }

    @Test
    fun pauseServerResultsHaveFeedbackButNeverUiRollback() {
        val calls = mutableListOf<String>()
        dispatchTimeshiftPlaybackAction(MediaPlaybackAction.TOGGLE, true,
            dispatch = { resume, rollback ->
                calls += "atomic pause"
                assertFalse(resume)
                for (result in listOf(TimeshiftCommandResult.REJECTED, TimeshiftCommandResult.TIMEOUT)) {
                    val completion = requireNotNull(timeshiftCommandCompletion(1, 1, result = result,
                        unavailableText = "unavailable", rollbackPlayWhenReady = rollback))
                    assertNull(completion.rollbackPlayWhenReady)
                    assertEquals(if (result == TimeshiftCommandResult.REJECTED) "unavailable" else null, completion.feedback)
                }
            })
        assertEquals(listOf("atomic pause"), calls)
    }

    @Test
    fun soundRestorationResultHasNoUnavailableFeedbackOrRollback() {
        val completion = requireNotNull(timeshiftCommandCompletion(
            1, 1, result = null, unavailableText = "unavailable", rollbackPlayWhenReady = null,
        ))
        assertNull(completion.feedback)
        assertNull(completion.rollbackPlayWhenReady)
    }

    @Test
    fun deniedResumeWithRejectedServerPauseCannotRollbackIntoAnotherFocusRequest() {
        val completion = requireNotNull(timeshiftCommandCompletion(
            1, 1, result = TimeshiftCommandResult.UNAVAILABLE,
            unavailableText = "unavailable", rollbackPlayWhenReady = false,
            interruptionOwned = true,
        ))
        assertNull(completion.rollbackPlayWhenReady)
    }

    @Test
    fun supersededRejectedCommandCannotRestoreFeedbackOrPlayIntent() {
        assertNull(
            timeshiftCommandCompletion(
                commandToken = 1,
                currentToken = 2,
                result = TimeshiftCommandResult.REJECTED,
                unavailableText = "unavailable",
                rollbackPlayWhenReady = true,
            ),
        )
    }

    @Test
    fun currentRejectedPauseReportsUnavailableAndRestoresPlayIntent() {
        val completion = requireNotNull(
            timeshiftCommandCompletion(
                commandToken = 2,
                currentToken = 2,
                result = TimeshiftCommandResult.REJECTED,
                unavailableText = "unavailable",
                rollbackPlayWhenReady = true,
            ),
        )

        assertEquals("unavailable", completion.feedback)
        assertEquals(true, completion.rollbackPlayWhenReady)
    }

    @Test
    fun currentRejectedResumeReportsUnavailableAndRestoresPauseIntent() {
        val completion = requireNotNull(
            timeshiftCommandCompletion(
                commandToken = 2,
                currentToken = 2,
                result = TimeshiftCommandResult.REJECTED,
                unavailableText = "unavailable",
                rollbackPlayWhenReady = false,
            ),
        )

        assertEquals("unavailable", completion.feedback)
        assertEquals(false, completion.rollbackPlayWhenReady)
    }

    @Test
    fun currentAcceptedCommandClearsFeedbackWithoutRollback() {
        val completion = requireNotNull(
            timeshiftCommandCompletion(
                commandToken = 3,
                currentToken = 3,
                feedbackToken = 3,
                currentFeedbackToken = 3,
                result = TimeshiftCommandResult.ACCEPTED,
                unavailableText = "unavailable",
                rollbackPlayWhenReady = true,
            ),
        )

        assertEquals(
            null,
            completion.feedback,
        )
        assertTrue(completion.applyFeedback)
        assertNull(completion.rollbackPlayWhenReady)
    }

    @Test
    fun currentUnconfirmedCommandWaitsForObservedStateWithoutRollback() {
        val completion = requireNotNull(
            timeshiftCommandCompletion(
                commandToken = 3,
                currentToken = 3,
                feedbackToken = 3,
                currentFeedbackToken = 3,
                result = TimeshiftCommandResult.TIMEOUT,
                unavailableText = "unavailable",
                rollbackPlayWhenReady = true,
            ),
        )

        assertNull(completion.feedback)
        assertTrue(completion.applyFeedback)
        assertNull(completion.rollbackPlayWhenReady)
    }

    @Test
    fun newerSeekSuppressesPauseFeedbackButNotRejectedPauseRollback() {
        val completion = requireNotNull(
            timeshiftCommandCompletion(
                commandToken = 4,
                currentToken = 4,
                feedbackToken = 4,
                currentFeedbackToken = 5,
                result = TimeshiftCommandResult.REJECTED,
                unavailableText = "unavailable",
                rollbackPlayWhenReady = true,
            ),
        )

        assertFalse(completion.applyFeedback)
        assertEquals(true, completion.rollbackPlayWhenReady)
    }
}
