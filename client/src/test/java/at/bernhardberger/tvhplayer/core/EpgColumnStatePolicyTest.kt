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
                windowCoverageProven = true,
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
            EpgColumnDataState.PARTIAL,
            epgColumnDataState(
                listOf(event(20, 80)),
                0,
                100,
                ConnectionUiState.Ready,
            ),
        )
    }

    @Test
    fun aFutureWindowIsEmptyOnlyOnceCoverageReachesItsEnd() {
        val channelId = ChannelId(1)
        // Three days ahead; the channel has programmes, just none loaded for this window.
        val windowStart = 3 * 86_400L
        val windowEnd = windowStart + 7_200L
        val now = 0L
        fun state(coverage: EpgCoverage?) = epgColumnDataState(
            visibleEvents = emptyList(),
            windowStartSec = windowStart,
            windowEndSec = windowEnd,
            connectionState = ConnectionUiState.Ready,
            coveragePending = false,
            hasCachedEvents = true,
            windowCoverageProven = epgWindowCoverageProven(coverage, windowEnd, now),
        )
        val short = EpgCoverage.create(
            channelId, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(86_400),
        )
        val partway = EpgCoverage.create(
            channelId, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(86_400),
            queriedTo = Instant.fromEpochSeconds(windowEnd - 1),
        )

        assertEquals(EpgColumnDataState.NOT_LOADED, state(null))
        assertEquals(EpgColumnDataState.NOT_LOADED, state(short))
        assertEquals(EpgColumnDataState.NOT_LOADED, state(partway))
        assertEquals(
            EpgColumnDataState.EMPTY_DAY,
            state(EpgCoverage.empty(channelId, queriedTo = Instant.fromEpochSeconds(windowEnd))),
        )
        assertEquals(
            EpgColumnDataState.EMPTY_DAY,
            state(
                EpgCoverage.create(
                    channelId, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(86_400),
                    queriedTo = Instant.fromEpochSeconds(windowEnd + 3_600),
                ),
            ),
        )
    }

    @Test
    fun anEndedWindowNeedsNoCoverageToBeEmpty() {
        assertEquals(true, epgWindowCoverageProven(null, windowEndSec = 100, nowSec = 100))
        assertEquals(false, epgWindowCoverageProven(null, windowEndSec = 101, nowSec = 100))
    }

    @Test
    fun anOnAirWindowNeedsCoverageToItsEndToBeEmpty() {
        val channelId = ChannelId(1)
        fun coverage(queriedTo: Long) = EpgCoverage.create(
            channelId, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(3_600),
            queriedTo = Instant.fromEpochSeconds(queriedTo),
        )

        assertEquals(false, epgWindowCoverageProven(null, windowEndSec = 7_200, nowSec = 3_600))
        assertEquals(false, epgWindowCoverageProven(coverage(7_199), windowEndSec = 7_200, nowSec = 3_600))
        assertEquals(true, epgWindowCoverageProven(coverage(7_200), windowEndSec = 7_200, nowSec = 3_600))
    }

    private fun event(start: Long, stop: Long) = EpgEvent.create(
        id = EventId(start),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = "Programme",
    )
}
