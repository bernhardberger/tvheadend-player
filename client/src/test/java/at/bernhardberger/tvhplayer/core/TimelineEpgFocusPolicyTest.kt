package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEpgFocusPolicyTest {
    @Test fun verticalAndPageMovesSkipEdgeSliversButKeepFullyVisibleShortProgrammes() {
        val readable = 40 * 60L until 170 * 60L
        val minimum = 12 * 60L
        val candidates = listOf(event(21, 0, 42), event(22, 42, 43), event(23, 168, 200))
        for (anchor in listOf(40 * 60L, 169 * 60L)) {
            for (direction in listOf(EpgFocusDirection.UP, EpgFocusDirection.DOWN)) {
                val source = row(1, event(11, 40, 170))
                val destination = row(2, *candidates.toTypedArray())
                val up = direction == EpgFocusDirection.UP
                val rows = if (up) listOf(destination, source) else listOf(source, destination)
                val current = focus(if (up) 1 else 0, 11)
                val expected = focus(if (up) 0 else 1, 22)
                assertEquals(expected, moveTimelineEpgFocus(rows, current, direction, anchorSec = anchor,
                    readableRangeSec = readable, minReadableOverlapSec = minimum).target)
                assertEquals(expected, timelinePageFocusTarget(rows, current, expected.channelIndex, if (up) -1 else 1,
                    anchorSec = anchor, readableRangeSec = readable, minReadableOverlapSec = minimum))
            }
        }
        assertTrue(timelineEventIsReadable(event(21, 0, 52), readable, minimum))
        assertTrue(timelineEventIsReadable(event(21, 158, 200), readable, minimum))
        assertFalse(timelineEventIsReadable(event(21, 0, 51), readable, minimum))
        assertFalse(timelineEventIsReadable(event(21, 159, 200), readable, minimum))
    }

    @Test fun onlySliversAboveFocusTheHeaderWhileOnlySliversBelowLeaveFocusInPlace() {
        val readable = 40 * 60L until 170 * 60L
        val rows = listOf(row(1, event(11, 0, 41)), row(2, event(21, 60, 100)), row(3, event(31, 169, 200)))
        val current = focus(1, 21)
        for (direction in listOf(EpgFocusDirection.UP, EpgFocusDirection.DOWN)) {
            val move = moveTimelineEpgFocus(rows, current, direction, readableRangeSec = readable, minReadableOverlapSec = 720)
            assertEquals(current, move.target)
            assertEquals(direction == EpgFocusDirection.UP, move.focusHeader)
            assertNull(timelinePageFocusTarget(rows, current, if (direction == EpgFocusDirection.UP) 0 else 2,
                if (direction == EpgFocusDirection.UP) -1 else 1, readableRangeSec = readable, minReadableOverlapSec = 720))
        }
    }

    @Test fun programmesAboveOnlyOutsideTheWindowFocusTheHeader() {
        val readable = 40 * 60L until 170 * 60L
        val rows = listOf(row(1, event(11, 0, 40), event(12, 170, 200)), row(2, event(21, 60, 100)))
        val current = focus(1, 21)
        val move = moveTimelineEpgFocus(rows, current, EpgFocusDirection.UP,
            readableRangeSec = readable, minReadableOverlapSec = 720)
        assertEquals(current, move.target)
        assertTrue(move.focusHeader)
    }

    @Test fun verticalMovesClampAnchorsAndExcludeReserveOnlyProgrammes() {
        val readable = 40 * 60L until 170 * 60L
        val rows = listOf(
            row(1, event(11, 0, 90)),
            row(2, event(21, -15, 40), event(22, 40, 100), event(23, 170, 200)),
        )
        for (anchor in listOf(30 * 60L, 40 * 60L)) {
            assertEquals(focus(1, 22), moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.DOWN,
                anchorSec = anchor, readableRangeSec = readable).target)
            assertEquals(focus(1, 22), timelinePageFocusTarget(rows, focus(0, 11), 1, 1,
                anchorSec = anchor, readableRangeSec = readable))
        }
        assertEquals(EventId(22), timelineEventAt(rows[1].events, 200 * 60L, readable)?.id)
        // Boundary overlap is half-open: neither an event ending at readStart nor one starting at readEnd counts.
        assertNull(timelineEventAt(listOf(event(21, 0, 40), event(23, 170, 200)), 30 * 60L, readable))
    }

    @Test fun verticalMovesSkipUnreadableRowsAndStopWhenNoneRemain() {
        val readable = 40 * 60L until 170 * 60L
        val rows = listOf(row(1, event(11, 0, 90)), row(2, event(21, 0, 40)), row(3, event(31, 30, 50)))
        assertEquals(focus(2, 31), moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.DOWN,
            readableRangeSec = readable).target)
        assertEquals(focus(2, 31), timelinePageFocusTarget(rows, focus(0, 11), 1, 1, readableRangeSec = readable))
        assertEquals(focus(0, 11), moveTimelineEpgFocus(rows.take(2), focus(0, 11), EpgFocusDirection.DOWN,
            readableRangeSec = readable).target)
        assertNull(timelinePageFocusTarget(rows.take(2), focus(0, 11), 1, 1, readableRangeSec = readable))
        assertEquals(focus(0, 11), moveTimelineEpgFocus(rows, focus(2, 31), EpgFocusDirection.UP,
            readableRangeSec = readable).target)
        assertEquals(focus(0, 11), timelinePageFocusTarget(rows, focus(2, 31), 1, -1, readableRangeSec = readable))
    }

    @Test
    fun viewportEligibilityKeepsPartialCellsAndExcludesWhollyClippedCells() {
        val clippedCells = listOf(990 to 310, 1135 to 165, 1083 to 217, 1010 to 290, 1038 to 262)
        clippedCells.forEach { (start, width) ->
            assertFalse(shouldComposeTimelineCell(start, width, 900, false))
        }
        assertTrue(shouldComposeTimelineCell(660, 330, 900, false))
        assertTrue(shouldComposeTimelineCell(899, 1, 900, false))
        assertFalse(shouldComposeTimelineCell(900, 1, 900, false))
        assertFalse(shouldComposeTimelineCell(0, 0, 900, false))
        assertFalse(shouldComposeTimelineCell(0, 100, 0, false))
    }

    @Test
    fun viewportEligibilityNeverDropsTheFocusTargetOrGuessesUnknownGeometry() {
        assertTrue(shouldComposeTimelineCell(990, 310, 900, true))
        assertTrue(shouldComposeTimelineCell(990, 310, null, false))
    }

    @Test
    fun channelEventIndexPreservesPerChannelSnapshotOrder() {
        val first = event(11, 0, 30)
        val otherChannel = event(21, 0, 60)
        val second = event(12, 30, 60)

        val indexed = indexTimelineEventsByChannel(
            events = listOf(first, otherChannel, second),
            windowStartSec = 0,
            windowEndSec = 60 * 60,
        ).visibleEventsByChannel

        assertEquals(listOf(first, second), indexed[ChannelId(1)])
        assertEquals(listOf(otherChannel), indexed[ChannelId(2)])
    }

    @Test
    fun pageFocusSkipsEmptyFilteredRowsFromPreferredPageTarget() {
        val rows = listOf(
            row(1, event(11, 0, 100)),
            row(2),
            row(3),
            row(4, event(41, 20, 120)),
            row(5),
        )

        assertEquals(
            EpgFocusTarget(channelIndex = 3, eventId = EventId(41)),
            timelinePageFocusTarget(
                rows = rows,
                current = EpgFocusTarget(channelIndex = 0, eventId = EventId(11)),
                preferredChannelIndex = 2,
                direction = 1,
            ),
        )
    }

    @Test
    fun pageFocusCanSearchBackwardAcrossEmptyFilteredRows() {
        val rows = listOf(
            row(1, event(11, 0, 100)),
            row(2),
            row(3, event(31, 20, 120)),
            row(4),
            row(5, event(51, 0, 100)),
        )

        assertEquals(
            EpgFocusTarget(channelIndex = 2, eventId = EventId(31)),
            timelinePageFocusTarget(
                rows = rows,
                current = EpgFocusTarget(channelIndex = 4, eventId = EventId(51)),
                preferredChannelIndex = 3,
                direction = -1,
            ),
        )
    }

    @Test
    fun pageFocusDoesNotSkipIntoAnUnrequestedLaterPage() {
        val pagedRows = (1..18).map { channelId ->
            when (channelId) {
                1 -> row(channelId, event(11, 0, 100))
                13 -> row(channelId, event(131, 20, 120))
                else -> row(channelId)
            }
        }

        assertNull(
            timelinePageFocusTarget(
                rows = pagedRows,
                current = EpgFocusTarget(channelIndex = 0, eventId = EventId(11)),
                preferredChannelIndex = 6,
                direction = 1,
                searchChannelIds = pagedRows
                    .subList(6, 12)
                    .mapTo(mutableSetOf()) { it.channelId },
            )
        )
    }

    @Test
    fun reconciliationKeepsExistingSemanticEventTarget() {
        val current = EpgFocusTarget(channelIndex = 1, eventId = EventId(21))

        assertEquals(
            current,
            reconcileTimelineEpgFocus(
                rows = rows,
                current = current,
                preferredChannelIndex = 0,
                targetSec = 0,
            ),
        )
    }

    @Test
    fun reconciliationMovesToNearestPopulatedRowWhenEventDisappears() {
        val filteredRows = listOf(
            row(1),
            row(2),
            row(3, event(31, 30, 90)),
        )

        assertEquals(
            focus(2, 31),
            reconcileTimelineEpgFocus(
                rows = filteredRows,
                current = focus(0, 11),
                preferredChannelIndex = 0,
                targetSec = 45 * 60,
            ),
        )
    }

    @Test
    fun reconciliationTracksSemanticEventAcrossChannelReordering() {
        val reordered = listOf(rows[1], rows[0], rows[2])

        assertEquals(
            focus(0, 21),
            reconcileTimelineEpgFocus(
                rows = reordered,
                current = focus(1, 21),
                preferredChannelIndex = 1,
                targetSec = 0,
            ),
        )
    }
    private val rows = listOf(
        row(1, event(11, 0, 60), event(12, 60, 120)),
        row(2, event(21, 0, 30), event(22, 30, 90), event(23, 90, 120)),
        row(3, event(31, 0, 120)),
    )

    @Test
    fun leftAndRightMoveChronologicallyWithinChannelRow() {
        assertEquals(
            EventId(12),
            moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.RIGHT).target.eventId,
        )
        assertEquals(
            EventId(11),
            moveTimelineEpgFocus(rows, focus(0, 12), EpgFocusDirection.LEFT).target.eventId,
        )
    }

    @Test
    fun upAndDownChooseTheAdjacentProgrammeOnAirAtTheFocusedStart() {
        val down = moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.DOWN)
        val up = moveTimelineEpgFocus(rows, down.target, EpgFocusDirection.UP)

        assertEquals(EventId(21), down.target.eventId)
        assertEquals(EventId(11), up.target.eventId)
    }

    @Test
    fun repeatedDownAndUpKeepTheAnchorTimeInsteadOfDrifting() {
        // D33: by best overlap, 11 (2:00) led to 21 and then 32, an hour earlier.
        val rows = listOf(
            row(1, event(11, 120, 180)),
            row(2, event(21, 50, 160), event(22, 160, 240)),
            row(3, event(31, 0, 60), event(32, 60, 120), event(33, 120, 180)),
        )
        val anchorSec = 120 * 60L

        val down = moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.DOWN, anchorSec = anchorSec)
        val downAgain = moveTimelineEpgFocus(rows, down.target, EpgFocusDirection.DOWN, anchorSec = anchorSec)
        val up = moveTimelineEpgFocus(rows, downAgain.target, EpgFocusDirection.UP, anchorSec = anchorSec)
        val upAgain = moveTimelineEpgFocus(rows, up.target, EpgFocusDirection.UP, anchorSec = anchorSec)

        assertEquals(focus(1, 21), down.target)
        assertEquals(focus(2, 33), downAgain.target)
        assertEquals(focus(1, 21), up.target)
        assertEquals(focus(0, 11), upAgain.target)
    }

    @Test
    fun anchorInAnEpgGapPicksTheNearestProgrammeAndTheLaterOneOnATie() {
        val events = listOf(event(11, 0, 60), event(12, 90, 120))

        assertEquals(EventId(11), timelineEventAt(events, 70 * 60L)?.id)
        assertEquals(EventId(12), timelineEventAt(events, 80 * 60L)?.id)
        assertEquals(EventId(12), timelineEventAt(events, 75 * 60L)?.id)
        assertEquals(EventId(11), timelineEventAt(events, 60 * 60L - 1)?.id)
        assertEquals(EventId(12), timelineEventAt(events, 90 * 60L)?.id)
    }

    @Test
    fun upFromFirstChannelMovesToCompactGuideHeader() {
        val move = moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.UP)

        assertTrue(move.focusHeader)
        assertEquals(focus(0, 11), move.target)
    }

    @Test
    fun navigationStopsAtTimelineAndLineupEndpoints() {
        val left = moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.LEFT)
        val down = moveTimelineEpgFocus(rows, focus(2, 31), EpgFocusDirection.DOWN)

        assertEquals(focus(0, 11), left.target)
        assertEquals(focus(2, 31), down.target)
        assertEquals(-1, left.timeFrontierDirection)
        assertFalse(left.pageChannels)
        assertFalse(down.pageChannels)
    }

    @Test
    fun rightPastLoadedProgrammeFrontierRequestsMoreGuideData() {
        val move = moveTimelineEpgFocus(rows, focus(0, 12), EpgFocusDirection.RIGHT)

        assertEquals(focus(0, 12), move.target)
        assertEquals(1, move.timeFrontierDirection)
    }

    @Test
    fun frontierResolutionStaysOnTheOriginatingChannel() {
        val newlyLoadedRows = listOf(
            row(2, event(24, 120, 180)),
            row(1, event(13, 120, 180)),
        )

        assertEquals(
            focus(1, 13),
            timelineFrontierFocus(
                rows = newlyLoadedRows,
                channelId = ChannelId(1),
                originEventId = EventId(12),
                boundarySec = 120 * 60,
                direction = 1,
            ),
        )
        assertNull(
            timelineFrontierFocus(
                rows = listOf(row(2, event(24, 120, 180)), row(1)),
                channelId = ChannelId(1),
                originEventId = EventId(12),
                boundarySec = 120 * 60,
                direction = 1,
            ),
        )
    }

    @Test
    fun crossingVisibleRowsRequestsChannelPage() {
        val move = moveTimelineEpgFocus(
            rows = rows,
            current = focus(1, 22),
            direction = EpgFocusDirection.DOWN,
            visibleChannelRange = 0..1,
        )

        assertTrue(move.pageChannels)
        assertEquals(EventId(31), move.target.eventId)
    }

    @Test
    fun verticalNavigationSkipsRowsWithoutFilteredEvents() {
        val filteredRows = listOf(
            row(1, event(11, 0, 60)),
            row(2),
            row(3),
            row(4, event(41, 30, 90)),
        )

        val down = moveTimelineEpgFocus(
            rows = filteredRows,
            current = focus(0, 11),
            direction = EpgFocusDirection.DOWN,
            visibleChannelRange = 0..2,
        )
        val up = moveTimelineEpgFocus(
            rows = filteredRows,
            current = down.target,
            direction = EpgFocusDirection.UP,
            visibleChannelRange = 1..3,
        )

        assertEquals(focus(3, 41), down.target)
        assertTrue(down.pageChannels)
        assertEquals(focus(0, 11), up.target)
        assertTrue(up.pageChannels)
    }

    @Test
    fun upAcrossOnlyEmptyRowsMovesToHeader() {
        val filteredRows = listOf(
            row(1),
            row(2),
            row(3, event(31, 0, 60)),
        )

        val move = moveTimelineEpgFocus(
            rows = filteredRows,
            current = focus(2, 31),
            direction = EpgFocusDirection.UP,
        )

        assertEquals(focus(2, 31), move.target)
        assertTrue(move.focusHeader)
    }

    @Test
    fun initialFocusSkipsEmptyPreferredRowAndUsesCurrentMatchingEvent() {
        val filteredRows = listOf(
            row(1),
            row(2, event(21, 0, 30), event(22, 30, 90)),
            row(3, event(31, 90, 120)),
        )

        val target = initialTimelineEpgFocus(
            rows = filteredRows,
            preferredChannelIndex = 0,
            targetSec = 45 * 60,
        )

        assertEquals(focus(1, 22), target)
    }

    @Test
    fun initialFocusPrefersRestoredEventWhenItStillMatches() {
        val target = initialTimelineEpgFocus(
            rows = rows,
            preferredChannelIndex = 1,
            preferredEventId = EventId(21),
            targetSec = 100 * 60,
        )

        assertEquals(focus(1, 21), target)
    }

    @Test
    fun eventSpansAreClippedToWindowWithoutMinimumWidthOverlap() {
        val first = timelineEventSpan(
            eventStartSec = 0,
            eventEndSec = 5 * 60,
            windowStartSec = 0,
            windowEndSec = 180 * 60,
        )
        val second = timelineEventSpan(
            eventStartSec = 5 * 60,
            eventEndSec = 10 * 60,
            windowStartSec = 0,
            windowEndSec = 180 * 60,
        )
        val clipped = timelineEventSpan(
            eventStartSec = -30 * 60,
            eventEndSec = 30 * 60,
            windowStartSec = 0,
            windowEndSec = 180 * 60,
        )

        assertEquals(first?.endFraction, second?.startFraction)
        assertEquals(0f, clipped?.startFraction)
        assertEquals(1f / 6f, clipped?.endFraction ?: 0f, 0.0001f)
        assertNull(timelineEventSpan(200, 300, 0, 100))
    }

    @Test
    fun initialFocusPreservesChannelAndEventIdsAboveIntRange() {
        val channelId = ChannelId(Int.MAX_VALUE.toLong() + 1L)
        val eventId = EventId(Int.MAX_VALUE.toLong() + 2L)
        val event = EpgEvent.create(
            id = eventId,
            channelId = channelId,
            start = Instant.fromEpochSeconds(0),
            stop = Instant.fromEpochSeconds(60),
            title = "High ID",
        )

        assertEquals(
            EpgFocusTarget(channelIndex = 0, eventId = eventId),
            initialTimelineEpgFocus(listOf(EpgFocusColumn(channelId, listOf(event))), 0, 30),
        )
    }

    private fun focus(channelIndex: Int, eventId: Int) = EpgFocusTarget(channelIndex, EventId(eventId.toLong()))

    private fun row(channelId: Int, vararg events: EpgEvent) =
        EpgFocusColumn(ChannelId(channelId.toLong()), events.toList())

    private fun event(id: Int, startMinutes: Long, stopMinutes: Long) = EpgEvent.create(
        id = EventId(id.toLong()),
        channelId = ChannelId((id / 10).toLong()),
        start = Instant.fromEpochSeconds(startMinutes * 60),
        stop = Instant.fromEpochSeconds(stopMinutes * 60),
        title = "Event $id",
    )
}
