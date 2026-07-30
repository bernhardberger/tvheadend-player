package at.bernhardberger.tvhplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastPlayedChannelPersistencePolicyTest {
    @Test
    fun liveTargetBClearsPlayingAAndRejectsALatePlayingCallbackFromA() {
        val tracker = LivePlayingServiceTracker()
        tracker.beginLiveTarget(serviceId = 10, generation = 1L)
        assertTrue(tracker.markLivePlaying(serviceId = 10, generation = 1L))
        assertEquals(10, tracker.serviceId.value)

        tracker.beginLiveTarget(serviceId = 20, generation = 2L)

        assertNull(tracker.serviceId.value)
        assertFalse(tracker.markLivePlaying(serviceId = 10, generation = 1L))
        assertNull(tracker.serviceId.value)
        assertFalse(shouldPersistDisplayedService(tracker, displayedServiceId = 20))
    }

    @Test
    fun rejectedBWithoutATargetTransitionCannotPersistDisplayedB() {
        val tracker = LivePlayingServiceTracker()
        tracker.beginLiveTarget(serviceId = 10, generation = 1L)
        assertTrue(tracker.markLivePlaying(serviceId = 10, generation = 1L))

        assertEquals(10, tracker.serviceId.value)
        assertFalse(shouldPersistDisplayedService(tracker, displayedServiceId = 20))
    }

    @Test
    fun supersededBPlayingCallbackCannotPublishBIntoGenerationC() {
        val tracker = LivePlayingServiceTracker()
        tracker.beginLiveTarget(serviceId = 20, generation = 2L)
        tracker.beginLiveTarget(serviceId = 30, generation = 3L)

        assertFalse(tracker.markLivePlaying(serviceId = 20, generation = 2L))
        assertNull(tracker.serviceId.value)
        assertFalse(shouldPersistDisplayedService(tracker, displayedServiceId = 20))

        assertTrue(tracker.markLivePlaying(serviceId = 30, generation = 3L))
        assertEquals(30, tracker.serviceId.value)
    }

    @Test
    fun recordingToBThenTerminalSupersessionNeverPublishesB() {
        val tracker = LivePlayingServiceTracker()
        tracker.beginLiveTarget(serviceId = 10, generation = 1L)
        assertTrue(tracker.markLivePlaying(serviceId = 10, generation = 1L))
        tracker.invalidate(generation = 2L)

        assertNull(tracker.serviceId.value)
        assertFalse(tracker.markLivePlaying(serviceId = 10, generation = 1L))

        tracker.beginLiveTarget(serviceId = 20, generation = 3L)
        tracker.invalidate(generation = 4L)

        assertFalse(tracker.markLivePlaying(serviceId = 20, generation = 3L))
        assertNull(tracker.serviceId.value)
        assertFalse(shouldPersistDisplayedService(tracker, displayedServiceId = 20))
    }

    @Test
    fun exactAcceptedPlayingBPublishesBForDisplayedServicePersistence() {
        val tracker = LivePlayingServiceTracker()
        tracker.beginLiveTarget(serviceId = 20, generation = 4L)

        assertFalse(shouldPersistDisplayedService(tracker, displayedServiceId = 20))
        assertTrue(tracker.markLivePlaying(serviceId = 20, generation = 4L))
        assertEquals(20, tracker.serviceId.value)
        assertTrue(shouldPersistDisplayedService(tracker, displayedServiceId = 20))
    }

    private fun shouldPersistDisplayedService(
        tracker: LivePlayingServiceTracker,
        displayedServiceId: Int,
    ): Boolean = tracker.serviceId.value == displayedServiceId
}
