package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.data.EpgEventEntry
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgColumnStatePolicyTest {
    @Test
    fun derivesDistinctInlineStates() {
        assertEquals(
            EpgColumnDataState.LOADING,
            epgColumnDataState(emptyList(), emptyList(), 0, 100, ConnectionUiState.SyncingChannels),
        )
        assertEquals(
            EpgColumnDataState.PERMISSION_DENIED,
            epgColumnDataState(
                emptyList(),
                emptyList(),
                0,
                100,
                ConnectionUiState.Error(ConnectionFailureKind.PERMISSION_DENIED),
            ),
        )
        assertEquals(
            EpgColumnDataState.STALE,
            epgColumnDataState(
                listOf(event(0, 100)),
                listOf(event(0, 100)),
                0,
                100,
                ConnectionUiState.Reconnecting,
            ),
        )
        assertEquals(
            EpgColumnDataState.EMPTY_DAY,
            epgColumnDataState(listOf(event(200, 300)), emptyList(), 0, 100, ConnectionUiState.Ready),
        )
        assertEquals(
            EpgColumnDataState.NO_DATA,
            epgColumnDataState(emptyList(), emptyList(), 0, 100, ConnectionUiState.Ready),
        )
        assertEquals(
            EpgColumnDataState.FILTER_EMPTY,
            epgColumnDataState(
                cachedEvents = listOf(event(0, 100)),
                visibleEvents = emptyList(),
                windowStartSec = 0,
                windowEndSec = 100,
                connectionState = ConnectionUiState.Ready,
                filterActive = true,
                matchingCachedEvents = emptyList(),
            ),
        )
        assertEquals(
            EpgColumnDataState.PARTIAL,
            epgColumnDataState(
                listOf(event(20, 80)),
                listOf(event(20, 80)),
                0,
                100,
                ConnectionUiState.Ready,
            ),
        )
    }

    private fun event(start: Long, stop: Long) = EpgEventEntry(
        eventId = start.toInt(),
        channelId = 1,
        start = start,
        stop = stop,
        title = "Programme",
    )
}
