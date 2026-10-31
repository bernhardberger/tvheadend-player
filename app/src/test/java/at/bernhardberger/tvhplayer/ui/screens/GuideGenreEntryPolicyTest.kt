package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Instant

class GuideGenreEntryPolicyTest {
    private val now = 1_800_000_000L
    private val channels = listOf(Channel.create(ChannelId(1)), Channel.create(ChannelId(2)))
    private fun event(id: Long, channel: Long, start: Long, stop: Long) = EpgEvent.create(
        EventId(id), ChannelId(channel), Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop),
        contentGenre = ContentGenre.fromContentType(0x40))

    @Test fun onNowWinsInChannelOrderEvenWhenAnUpcomingEventWasCachedFirst() {
        val first = event(1, 1, now - 10, now + 10)
        assertEquals(first, genreEntryProgramme(channels, listOf(event(2, 2, now + 10, now + 20),
            event(3, 2, now - 20, now + 20), first), ProgrammeCategory.SPORT, now))
    }

    @Test fun earliestUpcomingWinsAcrossChannelsAndEndedOrDistantEventsNeverWin() {
        val upcoming = event(2, 2, now + 3600, now + 7200)
        assertEquals(upcoming, genreEntryProgramme(channels, listOf(event(1, 1, now - 10, now),
            event(3, 1, now + 7200, now + 10800), upcoming), ProgrammeCategory.SPORT, now))
        assertNull(genreEntryProgramme(channels, listOf(event(1, 1, now - 10, now),
            event(4, 2, now + 86_401, now + 86_500)), ProgrammeCategory.SPORT, now))
    }
}
