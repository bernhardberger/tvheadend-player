package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.SubscriptionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EpgColumnStatePolicyTest {
    @Test
    fun frontierDecisionNeverUsesAStaleSnapshotAsCurrentCoverage() {
        val channelId = ChannelId(1)
        val requestedThrough = Instant.fromEpochSeconds(20_000)
        val next = event(10_000, 11_000)
        val snapshot = EpgSnapshot.create(
            events = listOf(next),
            coverages = listOf(EpgCoverage.empty(channelId, requestedThrough)),
        )

        assertNull(EpgRepositoryState.Stale(snapshot).currentEpgSnapshot())
        assertSame(snapshot, EpgRepositoryState.Current(snapshot).currentEpgSnapshot())
    }
    @Test
    fun derivesDistinctInlineStates() {
        assertEquals(
            EpgColumnDataState.LOADING,
            epgColumnDataState(emptyList(), 0, 100, ConnectionUiState.SyncingChannels),
        )
        assertEquals(
            EpgColumnDataState.PERMISSION_DENIED,
            epgColumnDataState(
                emptyList(),
                0,
                100,
                ConnectionUiState.Error(
                    ConnectionFailureKind.PERMISSION_DENIED,
                    SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
                ),
            ),
        )
        assertEquals(
            EpgColumnDataState.STALE,
            epgColumnDataState(
                listOf(event(0, 100)),
                0,
                100,
                ConnectionUiState.Reconnecting,
                hasCachedEvents = true,
            ),
        )
        assertEquals(
            EpgColumnDataState.EMPTY_DAY,
            epgColumnDataState(
                emptyList(),
                0,
                100,
                ConnectionUiState.Ready,
                hasCachedEvents = true,
            ),
        )
        assertEquals(
            EpgColumnDataState.NO_DATA,
            epgColumnDataState(emptyList(), 0, 100, ConnectionUiState.Ready),
        )
        assertEquals(
            EpgColumnDataState.LOADING,
            epgColumnDataState(
                visibleEvents = emptyList(),
                windowStartSec = 0,
                windowEndSec = 100,
                connectionState = ConnectionUiState.Ready,
                coveragePending = true,
                hasCachedEvents = true,
            ),
        )
        assertEquals(
            EpgColumnDataState.SERVER_FAILURE,
            epgColumnDataState(
                visibleEvents = emptyList(),
                windowStartSec = 0,
                windowEndSec = 100,
                connectionState = ConnectionUiState.SubscriptionError(
                    SubscriptionFailureKind.NO_INPUT
                ),
                coveragePending = true,
            ),
        )
        assertEquals(
            EpgColumnDataState.FILTER_EMPTY,
            epgColumnDataState(
                visibleEvents = emptyList(),
                windowStartSec = 0,
                windowEndSec = 100,
                connectionState = ConnectionUiState.Ready,
                filterActive = true,
                hasCachedEvents = true,
                hasMatchingCachedEvents = false,
            ),
        )
        assertEquals(
            EpgColumnDataState.PARTIAL,
            epgColumnDataState(
                listOf(event(20, 80)),
                0,
                100,
                ConnectionUiState.Ready,
            ),
        )
    }

    private fun event(start: Long, stop: Long) = EpgEvent.create(
        id = EventId(start),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = "Programme",
    )
}
