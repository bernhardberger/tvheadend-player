package at.bernhardberger.tvhplayer.player.htsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtspMuxDeliveryTrackerTest {
    @Test
    fun sequenceGapIsDetectedInsteadOfSilentlyLosingMux() {
        val tracker = HtspMuxDeliveryTracker(initialSequence = 39)

        assertTrue(tracker.accept(40))
        assertTrue(tracker.accept(41))
        assertFalse(tracker.accept(43))
    }

    @Test
    fun unstampedCompatibilityEventsDoNotCreateFalseLoss() {
        val tracker = HtspMuxDeliveryTracker(initialSequence = 0)

        assertTrue(tracker.accept(0))
        assertTrue(tracker.accept(0))
    }

    @Test
    fun firstStampedEventMustFollowRegistrationWatermark() {
        val tracker = HtspMuxDeliveryTracker(initialSequence = 50)

        assertFalse(tracker.accept(52))
    }
}
