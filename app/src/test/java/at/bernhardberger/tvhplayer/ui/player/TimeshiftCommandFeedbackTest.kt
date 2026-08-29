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
                restorePlayIntentOnFailure = true,
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
                restorePlayIntentOnFailure = true,
            ),
        )

        assertEquals("unavailable", completion.feedback)
        assertTrue(completion.restorePlayIntent)
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
                restorePlayIntentOnFailure = true,
            ),
        )

        assertEquals(
            null,
            completion.feedback,
        )
        assertTrue(completion.applyFeedback)
        assertFalse(completion.restorePlayIntent)
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
                restorePlayIntentOnFailure = true,
            ),
        )

        assertFalse(completion.applyFeedback)
        assertTrue(completion.restorePlayIntent)
    }
}
