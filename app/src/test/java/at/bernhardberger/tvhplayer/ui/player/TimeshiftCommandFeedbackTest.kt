package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeshiftCommandFeedbackTest {
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
