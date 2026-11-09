package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackKeyCycleTest {
    private val cycle = PlayerBackKeyCycle()
    private fun down(repeat: Int = 0, long: Boolean = false, allowed: Boolean = true) =
        cycle.onKey(true, repeat, long, false, allowed)
    private fun up(canceled: Boolean = false) = cycle.onKey(false, 0, false, canceled, true)

    @Test fun shortBackWaitsForReleaseAndResets() {
        assertEquals(PlayerBackKeyResult.NONE, down())
        assertEquals(PlayerBackKeyResult.BACK, up())
        assertEquals(PlayerBackKeyResult.NONE, up())
        assertEquals(PlayerBackKeyResult.NONE, down())
        assertEquals(PlayerBackKeyResult.BACK, up())
    }

    @Test fun repeatOrLongPressHoldsExactlyOnceAndReleaseDoesNotGoBack() {
        for (long in listOf(false, true)) {
            down()
            assertEquals(PlayerBackKeyResult.HOLD, down(if (long) 0 else 1, long = long))
            assertEquals(PlayerBackKeyResult.NONE, down(if (long) 0 else 2, long = long))
            assertEquals(PlayerBackKeyResult.NONE, up())
            down()
            assertEquals(PlayerBackKeyResult.BACK, up())
        }
    }

    @Test fun untrackedRepeatFlagAndReleaseDoNothing() {
        assertEquals(PlayerBackKeyResult.NONE, down(1))
        assertEquals(PlayerBackKeyResult.NONE, down(long = true))
        assertEquals(PlayerBackKeyResult.NONE, up())
    }

    @Test fun canceledReleaseDoesNothingAndResets() {
        down()
        assertEquals(PlayerBackKeyResult.NONE, up(canceled = true))
        assertEquals(PlayerBackKeyResult.NONE, down(1))
        down()
        assertEquals(PlayerBackKeyResult.BACK, up())
    }

    @Test fun disabledHoldLeavesShortBackUnchanged() {
        down(allowed = false)
        assertEquals(PlayerBackKeyResult.NONE, down(1, allowed = false))
        assertEquals(PlayerBackKeyResult.NONE, down(2, allowed = false))
        assertEquals(PlayerBackKeyResult.BACK, up())
    }

    @Test fun panelDisappearingDuringThePressDoesNotTurnItsBackIntoStop() {
        down(allowed = false)
        assertEquals(PlayerBackKeyResult.NONE, down(1, allowed = true))
        assertEquals(PlayerBackKeyResult.NONE, down(2, allowed = true))
        assertEquals(PlayerBackKeyResult.BACK, up())
    }

    @Test fun vanishedOrReplacedOwnerConsumesShortAndHeldReleases() {
        for (repeat in listOf(false, true)) {
            val original = Any()
            cycle.onKey(true, 0, false, false, false, original)
            if (repeat) cycle.onKey(true, 1, true, false, false, original)
            assertEquals(PlayerBackKeyResult.NONE,
                cycle.onKey(false, 0, false, false, true, Any()))
            assertEquals(PlayerBackKeyResult.NONE,
                cycle.onKey(false, 0, false, false, false, original))
        }
    }

    @Test fun unchangedOwnerDispatchesExactlyOneReleaseAndResetsForTheNextOwner() {
        for (owner in listOf(Any(), Any())) {
            cycle.onKey(true, 0, false, false, false, owner)
            cycle.onKey(true, 1, true, false, false, owner)
            assertEquals(PlayerBackKeyResult.BACK,
                cycle.onKey(false, 0, false, false, false, owner))
            assertEquals(PlayerBackKeyResult.NONE,
                cycle.onKey(false, 0, false, false, false, owner))
        }
    }

    @Test fun watchingPressWithPanelAppearingOnRepeatDoesNotStopOrBackThePanel() {
        cycle.onKey(true, 0, false, false, true, "watching")
        assertEquals(PlayerBackKeyResult.NONE,
            cycle.onKey(true, 1, true, false, false, "panel"))
        assertEquals(PlayerBackKeyResult.NONE,
            cycle.onKey(false, 0, false, false, false, "panel"))
    }
}
