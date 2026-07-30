package at.bernhardberger.tvhplayer.player.htsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramedRingBufferTest {
    @Test
    fun seekClearPreservesRemainderOfFrameAlreadyReadByExtractor() {
        val ring = FramedRingBuffer(capacity = 32)
        val oldFrame = byteArrayOf(1, 2, 3, 4, 5, 6)
        val staleFrame = byteArrayOf(7, 8, 9, 10)
        val postSeekFrame = byteArrayOf(11, 12, 13, 14, 15)
        val output = ByteArray(16)

        assertTrue(ring.write(oldFrame, 0, oldFrame.size) { true })
        assertTrue(ring.write(staleFrame, 0, staleFrame.size) { true })
        assertEquals(3, ring.read(output, 0, 3))

        ring.clearCompleteFramesForSeek()
        assertTrue(ring.write(postSeekFrame, 0, postSeekFrame.size) { true })

        val read = ring.read(output, 0, output.size)
        assertEquals(8, read)
        assertArrayEquals(
            byteArrayOf(4, 5, 6, 11, 12, 13, 14, 15),
            output.copyOf(read),
        )
    }

    @Test
    fun normalReadStopsAtLastCompleteFrameThatFits() {
        val ring = FramedRingBuffer(capacity = 32)
        val first = byteArrayOf(1, 2, 3, 4)
        val second = byteArrayOf(5, 6, 7, 8)
        val output = ByteArray(6)

        assertTrue(ring.write(first, 0, first.size) { true })
        assertTrue(ring.write(second, 0, second.size) { true })

        assertEquals(4, ring.read(output, 0, output.size))
        assertArrayEquals(first, output.copyOf(4))
    }

    @Test
    fun seekClearDropsAllBufferedFramesWhenNoneIsPartiallyRead() {
        val ring = FramedRingBuffer(capacity = 32)
        val stale = byteArrayOf(1, 2, 3, 4)
        val fresh = byteArrayOf(5, 6, 7)
        val output = ByteArray(8)

        assertTrue(ring.write(stale, 0, stale.size) { true })
        ring.clearCompleteFramesForSeek()
        assertTrue(ring.write(fresh, 0, fresh.size) { true })

        assertEquals(fresh.size, ring.read(output, 0, output.size))
        assertArrayEquals(fresh, output.copyOf(fresh.size))
    }
}
