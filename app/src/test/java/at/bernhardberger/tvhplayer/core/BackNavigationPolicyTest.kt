package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackNavigationPolicyTest {
    @Test
    fun confirmedSameWarmPlaybackSelectionRearmsOneReturn() {
        val consumed = consumeWarmReturn(armWarmReturn(WarmPlaybackTarget.LIVE))

        assertEquals(
            WarmReturnOpportunity(armed = true, target = WarmPlaybackTarget.LIVE),
            rearmWarmReturnForPlaybackSelection(
                current = consumed,
                currentWarmTarget = WarmPlaybackTarget.LIVE,
                requestedTarget = WarmPlaybackTarget.LIVE,
                currentIdentity = 42,
                requestedIdentity = 42,
            ),
        )
    }

    @Test
    fun confirmedPlaybackSelectionDoesNotInventWarmSession() {
        assertEquals(
            WarmReturnOpportunity(),
            rearmWarmReturnForPlaybackSelection(
                current = WarmReturnOpportunity(),
                currentWarmTarget = WarmPlaybackTarget.NONE,
                requestedTarget = WarmPlaybackTarget.RECORDING,
                currentIdentity = null,
                requestedIdentity = 7,
            ),
        )
    }

    @Test
    fun differentLiveSelectionWaitsForPlaybackStartBeforeArming() {
        val consumed = consumeWarmReturn(armWarmReturn(WarmPlaybackTarget.LIVE))

        assertEquals(
            consumed,
            rearmWarmReturnForPlaybackSelection(
                current = consumed,
                currentWarmTarget = WarmPlaybackTarget.LIVE,
                requestedTarget = WarmPlaybackTarget.LIVE,
                currentIdentity = 42,
                requestedIdentity = 43,
            ),
        )
    }
    @Test
    fun rootStartDestinationFinishesActivityWithoutWarmReturn() {
        assertEquals(
            BackAction.FINISH_ACTIVITY,
            rootBackAction(
                isStartDestination = true,
                warmReturn = WarmReturnOpportunity(),
            ),
        )
    }

    @Test
    fun rootStartDestinationReturnsToArmedWarmPlayback() {
        assertEquals(
            BackAction.RETURN_TO_PLAYER,
            rootBackAction(
                isStartDestination = true,
                warmReturn = armWarmReturn(WarmPlaybackTarget.LIVE),
            ),
        )
    }

    @Test
    fun rootStartDestinationReturnsToArmedWarmRecording() {
        assertEquals(
            BackAction.RETURN_TO_PLAYER,
            rootBackAction(
                isStartDestination = true,
                warmReturn = armWarmReturn(WarmPlaybackTarget.RECORDING),
            ),
        )
    }

    @Test
    fun rootStartDestinationFinishesWhenWarmPlaybackExistsButReturnWasConsumed() {
        val consumed = consumeWarmReturn(armWarmReturn(WarmPlaybackTarget.LIVE))
        assertFalse(consumed.canReturn)
        assertEquals(WarmPlaybackTarget.LIVE, consumed.target)
        assertEquals(
            BackAction.FINISH_ACTIVITY,
            rootBackAction(isStartDestination = true, warmReturn = consumed),
        )
    }

    @Test
    fun rootChildDestinationPopsNavigationEvenWhenWarmReturnArmed() {
        assertEquals(
            BackAction.POP_NAVIGATION,
            rootBackAction(
                isStartDestination = false,
                warmReturn = armWarmReturn(WarmPlaybackTarget.LIVE),
            ),
        )
    }

    @Test
    fun warmReturnIsOneShotAcrossBrowsePlayerBrowseCycle() {
        var warm = armWarmReturn(WarmPlaybackTarget.LIVE)

        // First root Back returns to the warm player and consumes the token.
        assertEquals(
            BackAction.RETURN_TO_PLAYER,
            rootBackAction(isStartDestination = true, warmReturn = warm),
        )
        warm = consumeWarmReturn(warm)

        // Player Back leaves playback warm but must not re-arm by itself.
        assertFalse(warm.canReturn)
        assertEquals(WarmPlaybackTarget.LIVE, warm.target)

        // Second root Back finishes instead of looping.
        assertEquals(
            BackAction.FINISH_ACTIVITY,
            rootBackAction(isStartDestination = true, warmReturn = warm),
        )
    }

    @Test
    fun deliberateNavigationRearmsOneWarmReturn() {
        var warm = consumeWarmReturn(armWarmReturn(WarmPlaybackTarget.LIVE))
        assertFalse(warm.canReturn)

        warm = rearmWarmReturn(WarmPlaybackTarget.LIVE)
        assertTrue(warm.canReturn)
        assertEquals(
            BackAction.RETURN_TO_PLAYER,
            rootBackAction(isStartDestination = true, warmReturn = warm),
        )
    }

    @Test
    fun newPlaybackArmsWarmReturn() {
        val warm = armWarmReturn(WarmPlaybackTarget.RECORDING)
        assertTrue(warm.canReturn)
        assertEquals(WarmPlaybackTarget.RECORDING, warm.target)
    }

    @Test
    fun explicitStopClearsWarmReturn() {
        val warm = clearWarmReturn()
        assertFalse(warm.canReturn)
        assertEquals(WarmPlaybackTarget.NONE, warm.target)
        assertEquals(
            BackAction.FINISH_ACTIVITY,
            rootBackAction(isStartDestination = true, warmReturn = warm),
        )
    }

    @Test
    fun warmPlaybackTargetPrefersLiveOverRecording() {
        assertEquals(
            WarmPlaybackTarget.LIVE,
            warmPlaybackTarget(
                activeServiceId = ChannelId(7),
                activeRecordingId = DvrEntryId(3),
            ),
        )
        assertEquals(
            WarmPlaybackTarget.RECORDING,
            warmPlaybackTarget(activeServiceId = null, activeRecordingId = DvrEntryId(3)),
        )
        assertEquals(
            WarmPlaybackTarget.NONE,
            warmPlaybackTarget(activeServiceId = null, activeRecordingId = null),
        )
    }

    @Test
    fun nestedStartDestinationReturnsToParentGraph() {
        assertEquals(
            BackAction.RETURN_TO_PARENT,
            nestedBackAction(hasPreviousEntry = false),
        )
    }

    @Test
    fun nestedChildDestinationPopsNestedNavigation() {
        assertEquals(
            BackAction.POP_NAVIGATION,
            nestedBackAction(hasPreviousEntry = true),
        )
    }
}
