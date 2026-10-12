@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.exoplayer.DecoderCounters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDecoderCountersTest {
    @Test fun snapshotMapsOutputCountersWithoutInputCountersOrAccumulation() {
        val counters = DecoderCounters().apply {
            renderedOutputBufferCount = 12345
            droppedBufferCount = 6
            skippedOutputBufferCount = 2
            skippedInputBufferCount = 88
        }
        val snapshot = counters.snapshotVideoDecoderCounters()
        assertEquals(AppVideoDecoderCounters(12345, 6, 2), snapshot)
        counters.renderedOutputBufferCount = 0
        assertEquals(12345, snapshot.renderedOutputBufferCount)
        assertEquals(0, counters.snapshotVideoDecoderCounters().renderedOutputBufferCount)
        assertEquals(AppVideoDecoderCounters(0, 0, 0), DecoderCounters().snapshotVideoDecoderCounters())
    }

    @Test fun absentDecoderIsNotReportedAsZeroBuffers() {
        assertNull(AppPlaybackDiagnostics().videoDecoderCounters)
    }
}
