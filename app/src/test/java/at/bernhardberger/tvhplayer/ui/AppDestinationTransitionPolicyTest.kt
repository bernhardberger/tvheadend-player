package at.bernhardberger.tvhplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTransitionPolicyTest {
    @Test
    fun destinationCrossfadeIsBriefAndExplicit() {
        assertEquals(150, APP_DESTINATION_CROSSFADE_DURATION_MILLIS)
    }
}
