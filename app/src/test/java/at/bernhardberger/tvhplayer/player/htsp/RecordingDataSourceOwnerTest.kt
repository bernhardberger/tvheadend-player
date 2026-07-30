package at.bernhardberger.tvhplayer.player.htsp

import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingDataSourceOwnerTest {
    @Test
    fun teardownClosesEveryDataSourceCreatedByTheFactoryExactlyOnce() {
        val owner = RecordingDataSourceOwner()
        val first = CountingCloseable()
        val second = CountingCloseable()
        owner.register(first)
        owner.register(second)

        owner.releaseAll()
        owner.releaseAll()

        assertEquals(1, first.closeCount)
        assertEquals(1, second.closeCount)
    }

    @Test
    fun registrationAfterTeardownIsClosedImmediately() {
        val owner = RecordingDataSourceOwner()
        owner.releaseAll()
        val late = CountingCloseable()

        owner.register(late)

        assertEquals(1, late.closeCount)
    }

    private class CountingCloseable : Closeable {
        var closeCount = 0

        override fun close() {
            closeCount++
        }
    }
}
