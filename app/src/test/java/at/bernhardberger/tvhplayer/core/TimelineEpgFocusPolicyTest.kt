package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.data.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEpgFocusPolicyTest {
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
            EpgFocusTarget(channelIndex = 3, eventId = 41),
            timelinePageFocusTarget(
                rows = rows,
                current = EpgFocusTarget(channelIndex = 0, eventId = 11),
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
            EpgFocusTarget(channelIndex = 2, eventId = 31),
            timelinePageFocusTarget(
                rows = rows,
                current = EpgFocusTarget(channelIndex = 4, eventId = 51),
                preferredChannelIndex = 3,
                direction = -1,
            ),
        )
    }

    @Test
    fun reconciliationKeepsExistingSemanticEventTarget() {
        val current = EpgFocusTarget(channelIndex = 1, eventId = 21)

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
            12,
            moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.RIGHT).target.eventId,
        )
        assertEquals(
            11,
            moveTimelineEpgFocus(rows, focus(0, 12), EpgFocusDirection.LEFT).target.eventId,
        )
    }

    @Test
    fun upAndDownChooseBestTimeOverlapInAdjacentChannel() {
        val down = moveTimelineEpgFocus(rows, focus(0, 11), EpgFocusDirection.DOWN)
        val up = moveTimelineEpgFocus(rows, down.target, EpgFocusDirection.UP)

        assertEquals(21, down.target.eventId)
        assertEquals(11, up.target.eventId)
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
        assertFalse(left.pageChannels)
        assertFalse(down.pageChannels)
    }

    @Test
    fun rightPastLoadedProgrammeFrontierRequestsMoreGuideData() {
        val move = moveTimelineEpgFocus(rows, focus(0, 12), EpgFocusDirection.RIGHT)

        assertEquals(focus(0, 12), move.target)
        assertTrue(move.extendTimeFrontier)
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
        assertEquals(31, move.target.eventId)
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
            preferredEventId = 21,
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
    fun syntheticThreeHundredChannelLineupHasDeterministicVerticalFocusGraph() {
        val large = (0 until 300).map { index ->
            row(index, event(index + 1, 0, 60))
        }
        var current = focus(0, 1)

        repeat(299) {
            current = moveTimelineEpgFocus(
                rows = large,
                current = current,
                direction = EpgFocusDirection.DOWN,
                visibleChannelRange = (current.channelIndex / 5 * 5)..(
                    current.channelIndex / 5 * 5 + 4
                ).coerceAtMost(299),
            ).target
        }

        assertEquals(299, current.channelIndex)
        assertEquals(300, current.eventId)
    }

    private fun focus(channelIndex: Int, eventId: Int) = EpgFocusTarget(channelIndex, eventId)

    private fun row(channelId: Int, vararg events: EpgEventEntry) =
        EpgFocusColumn(channelId, events.toList())

    private fun event(id: Int, startMinutes: Long, stopMinutes: Long) = EpgEventEntry(
        eventId = id,
        channelId = id / 10,
        start = startMinutes * 60,
        stop = stopMinutes * 60,
        title = "Event $id",
    )
}
