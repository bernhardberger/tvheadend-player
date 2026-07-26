package at.bernhardberger.tvhplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test
    fun rollingReadRateUsesOnlyBytesReadSinceThePreviousSample() {
        assertEquals(
            24_000L,
            readRateBitsPerSecond(
                previousBytes = 1_000L,
                currentBytes = 2_500L,
                elapsedMillis = 500L,
            ),
        )
    }

    @Test
    fun rollingReadRateRejectsAnInvalidOrResetSample() {
        assertNull(readRateBitsPerSecond(2_500L, 1_000L, 500L))
        assertNull(readRateBitsPerSecond(1_000L, 2_500L, 0L))
    }

    @Test
    fun decoderCounterDeltaIsBaselinedAndNeverNegative() {
        assertEquals(25, decoderCounterDelta(current = 125, baseline = 100))
        assertEquals(0, decoderCounterDelta(current = 5, baseline = 100))
    }

    @Test
    fun droppedFramePercentageHandlesEmptyAndActiveSessions() {
        assertNull(droppedFramePercentage(rendered = 0, dropped = 0))
        assertEquals(2.5f, droppedFramePercentage(rendered = 390, dropped = 10)!!, 0.001f)
    }

    @Test
    fun androidThermalStatusMapsWithoutLeakingPlatformIntegersIntoTheUi() {
        assertEquals(PlaybackThermalLevel.NONE, playbackThermalLevel(0))
        assertEquals(PlaybackThermalLevel.MODERATE, playbackThermalLevel(2))
        assertEquals(PlaybackThermalLevel.SHUTDOWN, playbackThermalLevel(6))
        assertNull(playbackThermalLevel(-1))
    }

    @Test
    fun relativeTunerValuesUseTvheadendsUnsignedSixteenBitScale() {
        assertEquals(0f, relativeSignalPercent(0L)!!, 0.001f)
        assertEquals(50f, relativeSignalPercent(32_768L)!!, 0.01f)
        assertEquals(100f, relativeSignalPercent(65_535L)!!, 0.001f)
        assertNull(relativeSignalPercent(65_536L))
    }
}
