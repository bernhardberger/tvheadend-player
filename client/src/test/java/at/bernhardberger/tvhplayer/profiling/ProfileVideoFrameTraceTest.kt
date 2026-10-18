package at.bernhardberger.tvhplayer.profiling

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileVideoFrameTraceTest {
    @Test
    fun frameMarkerCarriesBothDeltasInMicroseconds() {
        assertEquals("P44:frame:pts:20000:release:19980", videoFrameMarker(20_000L, 19_980L))
        assertEquals("P44:frame:pts:0:release:-3", videoFrameMarker(0L, -3L))
    }
}
