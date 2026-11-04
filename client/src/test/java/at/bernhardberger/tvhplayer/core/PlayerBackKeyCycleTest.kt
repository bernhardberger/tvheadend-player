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

    @Test fun firstRepeatHoldsExactlyOnceAndReleaseDoesNotGoBack() {
        down()
        assertEquals(PlayerBackKeyResult.HOLD, down(1))
        assertEquals(PlayerBackKeyResult.NONE, down(2))
        assertEquals(PlayerBackKeyResult.NONE, up())
        down()
        assertEquals(PlayerBackKeyResult.BACK, up())
    }

    @Test fun longPressFlagIsEnoughWhileTracking() {
        down()
        assertEquals(PlayerBackKeyResult.HOLD, down(long = true))
        assertEquals(PlayerBackKeyResult.NONE, down(long = true))
        assertEquals(PlayerBackKeyResult.NONE, up())
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
        down()
        assertEquals(PlayerBackKeyResult.NONE, down(1, allowed = false))
        assertEquals(PlayerBackKeyResult.NONE, down(2, allowed = false))
        assertEquals(PlayerBackKeyResult.BACK, up())
    }
}
