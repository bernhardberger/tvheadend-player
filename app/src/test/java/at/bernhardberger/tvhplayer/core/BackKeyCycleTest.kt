package at.bernhardberger.tvhplayer.core

import org.junit.Assert.*
import org.junit.Test

class BackKeyCycleTest {
    @Test fun normalPressActsOnlyOnRelease() {
        val cycle = BackKeyCycle()
        var actions = 0
        assertTrue(cycle.handle(true, 0, false) { actions++ })
        assertEquals(0, actions)
        assertTrue(cycle.handle(false, 0, false) { actions++ })
        assertEquals(1, actions)
    }

    @Test fun heldPressActsOnceOnRelease() {
        val cycle = BackKeyCycle()
        var actions = 0
        cycle.handle(true, 0, false) { actions++ }
        for (repeat in 1..5) cycle.handle(true, repeat, false) { actions++ }
        assertEquals(0, actions)
        cycle.handle(false, 0, false) { actions++ }
        cycle.handle(false, 0, false) { actions++ }
        assertEquals(1, actions)
    }

    @Test fun canceledAndUnseenReleasesDoNothing() {
        val cycle = BackKeyCycle()
        fun key(down: Boolean, repeat: Int = 0, canceled: Boolean = false) =
            cycle.handle(down, repeat, canceled) { error("Unexpected Back") }
        assertTrue(key(false))
        key(true, repeat = 1)
        key(false)
        key(true)
        key(false, canceled = true)
        key(false)
    }

    @Test fun freshDownRecoversLostReleaseAndDisabledOwnerForgetsPress() {
        val cycle = BackKeyCycle()
        var actions = 0
        cycle.handle(true, 0, false) { actions++ }
        assertFalse(cycle.handle(true, 1, false, enabled = false) { actions++ })
        cycle.handle(false, 0, false) { actions++ }
        assertEquals(0, actions)
        cycle.handle(true, 0, false) { actions++ }
        cycle.handle(true, 0, false) { actions++ }
        cycle.handle(false, 0, false) { actions++ }
        assertEquals(1, actions)
    }
}
