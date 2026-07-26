package at.bernhardberger.tvhplayer.stores

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleTvSessionTest {
    @Test
    fun startAndExitOnlyChangeCurrentSession() {
        val session = SimpleTvSession()

        assertFalse(session.active.value)

        session.start()
        assertTrue(session.active.value)

        session.exit()
        assertFalse(session.active.value)
    }
}
