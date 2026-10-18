package at.bernhardberger.tvhplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusyRingQuietTest {
    private val covered = AppVideoPresentation(epoch = 3L, visible = false, soundSinceMillis = 1_000L)

    @Test fun soundUnderTheCoverQuietsTheRingOnceThePictureStayedStillForTheGrace() {
        assertEquals(300L, covered.busyRingQuietInMillis(nowMillis = 1_000L))
        assertEquals(1L, covered.busyRingQuietInMillis(nowMillis = 1_299L))
        assertEquals(0L, covered.busyRingQuietInMillis(nowMillis = 1_300L))
        assertEquals("a late look is quiet already", 0L, covered.busyRingQuietInMillis(nowMillis = 5_000L))
    }

    @Test fun noSoundOrAMovingPictureNeverQuietsTheRing() {
        assertNull(covered.copy(soundSinceMillis = null).busyRingQuietInMillis(nowMillis = 9_000L))
        assertNull(covered.copy(visible = true).busyRingQuietInMillis(nowMillis = 9_000L))
        assertNull(AppVideoPresentation().busyRingQuietInMillis(nowMillis = 9_000L))
    }
}
