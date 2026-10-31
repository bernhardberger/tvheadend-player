package at.bernhardberger.tvhplayer.ui.screens.home

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.resolveChannelScope
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Instant

class HomeInputTest {
    private val now = 1_800_000_000L

    @Test fun mismatchedScopeAndSessionCannotBuildMixedRows() {
        val oldChannel = Channel.create(ChannelId(1), name = "Previous")
        val newChannel = Channel.create(ChannelId(2), name = "Current")
        val old = observation(oldChannel)
        val current = observation(newChannel)
        assertNotSame(old.currentSession, current.currentSession)
        val oldScope = ChannelScopeState(resolveChannelScope(listOf(oldChannel), emptyList(), null),
            channelCatalogCurrent = true, observation = old)
        assertNull(homeInput(oldScope, current, listOf(newChannel.id), null, now))
        val retained = requireNotNull(homeInput(oldScope, old, listOf(oldChannel.id), null, now))
        assertEquals(listOf(oldChannel), retained.channels)
        assertEquals(setOf(oldChannel.id), retained.currentEvents.keys)
        val newScope = oldScope.copy(scope = resolveChannelScope(listOf(newChannel), emptyList(), null), observation = current)
        val aligned = requireNotNull(homeInput(newScope, current, listOf(newChannel.id), null, now))
        assertEquals(listOf(newChannel), aligned.channels)
        assertEquals(setOf(newChannel.id), aligned.currentEvents.keys)
    }

    private fun observation(channel: Channel): SessionObservation = FakeTvheadendSession(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(EpgEvent.create(
            EventId(channel.id.value), channel.id, Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = channel.name)))),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).observation.value
}
