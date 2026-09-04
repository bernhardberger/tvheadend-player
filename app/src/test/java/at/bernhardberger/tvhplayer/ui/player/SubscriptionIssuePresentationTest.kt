package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionIssuePresentationTest {
    @Test
    fun noInputKeepsItsSpecificPresentation() {
        assertEquals(R.string.tvh_no_input, SubscriptionIssue.NO_INPUT.messageResource())
    }

    @Test
    fun unknownIssueUsesTheFutureValueFallback() {
        assertEquals(R.string.player_playback_failed, SubscriptionIssue.UNKNOWN.messageResource())
    }
}
