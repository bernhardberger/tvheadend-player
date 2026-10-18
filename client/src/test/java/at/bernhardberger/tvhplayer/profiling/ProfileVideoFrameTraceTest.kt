package at.bernhardberger.tvhplayer.profiling

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class ProfileVideoFrameTraceTest {
    @Test
    fun frameMarkerCarriesBothDeltasInMicroseconds() {
        assertEquals("P44:frame:pts:20000:release:19980", videoFrameMarker(20_000L, 19_980L))
        assertEquals("P44:frame:pts:0:release:-3", videoFrameMarker(0L, -3L))
    }

    @Test
    fun declaredMarkerCarriesTheFrameDurationInMicrosecondsOrNone() {
        assertEquals("P44:frame:declared:40000", declaredFrameDurationMarker(40.milliseconds))
        assertEquals("P44:frame:declared:33367", declaredFrameDurationMarker(33_367.microseconds))
        assertEquals("P44:frame:declared:none", declaredFrameDurationMarker(null))
    }
}
