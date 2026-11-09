package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackNavigationPolicyTest {
    @Test
    fun playbackSelectionRearmsOnlyTheSameExistingWarmSession() {
        val consumed = consumeWarmReturn(armWarmReturn(WarmPlaybackTarget.LIVE))
        for ((current, requestedIdentity, expected) in listOf(
            Triple(consumed, 42, WarmReturnOpportunity(armed = true, target = WarmPlaybackTarget.LIVE)),
            Triple(WarmReturnOpportunity(), 7, WarmReturnOpportunity()),
            Triple(consumed, 43, consumed),
        )) {
            val existing = current.target == WarmPlaybackTarget.LIVE
            assertEquals(expected, rearmWarmReturnForPlaybackSelection(
                current = current,
                currentWarmTarget = if (existing) WarmPlaybackTarget.LIVE else WarmPlaybackTarget.NONE,
                requestedTarget = if (existing) WarmPlaybackTarget.LIVE else WarmPlaybackTarget.RECORDING,
                currentIdentity = if (existing) 42 else null,
                requestedIdentity = requestedIdentity,
            ))
        }
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
        for (target in listOf(WarmPlaybackTarget.LIVE, WarmPlaybackTarget.RECORDING)) {
            var warm = armWarmReturn(target)

            // First root Back returns to the warm player and consumes the token.
            assertEquals(
                BackAction.RETURN_TO_PLAYER,
                rootBackAction(isStartDestination = true, warmReturn = warm),
            )
            warm = consumeWarmReturn(warm)

            // Player Back leaves playback warm but must not re-arm by itself.
            assertFalse(warm.canReturn)
            assertEquals(target, warm.target)

            // Second root Back finishes instead of looping.
            assertEquals(
                BackAction.FINISH_ACTIVITY,
                rootBackAction(isStartDestination = true, warmReturn = warm),
            )
        }
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
    fun newPlaybackArmsWarmReturnAndExplicitStopClearsIt() {
        var warm = armWarmReturn(WarmPlaybackTarget.RECORDING)
        assertTrue(warm.canReturn)
        assertEquals(WarmPlaybackTarget.RECORDING, warm.target)
        warm = clearWarmReturn()
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

}
