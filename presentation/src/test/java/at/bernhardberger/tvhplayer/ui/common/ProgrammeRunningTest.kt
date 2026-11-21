package at.bernhardberger.tvhplayer.ui.common

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ProgrammeRunningTest {
    @Test fun runningIncludesStartButExcludesStop() {
        val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200))
        assertFalse(event.isRunning(99))
        assertTrue(event.isRunning(100))
        assertTrue(event.isRunning(199))
        assertFalse(event.isRunning(200))
        assertFalse(event.isRunning(201))
    }

    @Test fun emptyWindowIsNeverRunning() {
        val empty = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(100))
        assertFalse(empty.isRunning(100))
    }
}
