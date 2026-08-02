package at.bernhardberger.tvhplayer.htsp

import org.junit.Assert.assertEquals
import org.junit.Test

class DvrStateMappingTest {
    @Test
    fun wireStateMapperIsOwnedByHtspAndMapsToStableDomainStates() {
        Class.forName("at.bernhardberger.tvhplayer.htsp.DvrStateMappingKt")
            .getDeclaredMethod("dvrState", String::class.java, String::class.java)

        assertEquals(DvrState.CANCELLED, dvrState("completed", "User cancelled"))
        assertEquals(DvrState.RECORDING, dvrState("running"))
        assertEquals(DvrState.SCHEDULED, dvrState("pending"))
        assertEquals(DvrState.COMPLETED, dvrState("finished"))
        assertEquals(DvrState.FAILED, dvrState("completed", "No free tuner"))
        assertEquals(DvrState.FAILED, dvrState(null, "No signal"))
        assertEquals(DvrState.UNKNOWN, dvrState("mystery"))
    }
}
