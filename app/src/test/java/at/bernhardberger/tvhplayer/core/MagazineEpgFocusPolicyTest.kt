package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazineEpgFocusPolicyTest {
    private val columns = listOf(
        column(1, event(11, 0, 60), event(12, 60, 120)),
        column(2, event(21, 0, 30), event(22, 30, 90), event(23, 90, 120)),
        column(3, event(31, 0, 120)),
    )

    @Test
    fun upAndDownMoveChronologicallyWithinColumn() {
        assertEquals(12, moveMagazineEpgFocus(columns, focus(0, 11), EpgFocusDirection.DOWN).target.eventId)
        assertEquals(11, moveMagazineEpgFocus(columns, focus(0, 12), EpgFocusDirection.UP).target.eventId)
    }

    @Test
    fun upPastFirstProgrammeMovesToDayStrip() {
        val move = moveMagazineEpgFocus(columns, focus(0, 11), EpgFocusDirection.UP)

        assertTrue(move.focusDayStrip)
        assertEquals(11, move.target.eventId)
    }

    @Test
    fun downPastFrontierRequestsContinuousExtension() {
        val move = moveMagazineEpgFocus(columns, focus(0, 12), EpgFocusDirection.DOWN)

        assertTrue(move.extendTimeFrontier)
        assertEquals(12, move.target.eventId)
    }

    @Test
    fun horizontalMoveChoosesBestTimeOverlap() {
        val right = moveMagazineEpgFocus(columns, focus(0, 11), EpgFocusDirection.RIGHT)
        val left = moveMagazineEpgFocus(columns, right.target, EpgFocusDirection.LEFT)

        assertEquals(21, right.target.eventId)
        assertEquals(11, left.target.eventId)
    }

    @Test
    fun horizontalMovementDoesNotWrapAtLineupEnds() {
        val first = moveMagazineEpgFocus(columns, focus(0, 11), EpgFocusDirection.LEFT)
        val last = moveMagazineEpgFocus(columns, focus(2, 31), EpgFocusDirection.RIGHT)

        assertEquals(11, first.target.eventId)
        assertEquals(31, last.target.eventId)
        assertFalse(first.pageColumns)
        assertFalse(last.pageColumns)
    }

    @Test
    fun crossingVisiblePageRequestsFullColumnPage() {
        val move = moveMagazineEpgFocus(
            columns = columns,
            current = focus(1, 22),
            direction = EpgFocusDirection.RIGHT,
            visibleColumnRange = 0..1,
        )

        assertTrue(move.pageColumns)
        assertEquals(31, move.target.eventId)
    }

    @Test
    fun syntheticThreeHundredChannelLineupHasDeterministicFocusGraph() {
        val large = (0 until 300).map { index ->
            column(index, event(index + 1, 0, 60))
        }
        var current = focus(0, 1)

        repeat(299) {
            current = moveMagazineEpgFocus(
                columns = large,
                current = current,
                direction = EpgFocusDirection.RIGHT,
                visibleColumnRange = (current.channelIndex / 4 * 4)..(
                    current.channelIndex / 4 * 4 + 3
                ).coerceAtMost(299),
            ).target
        }

        assertEquals(299, current.channelIndex)
        assertEquals(300, current.eventId)
    }

    private fun focus(channelIndex: Int, eventId: Int) = EpgFocusTarget(channelIndex, eventId)

    private fun column(channelId: Int, vararg events: EpgEventEntry) =
        EpgFocusColumn(channelId, events.toList())

    private fun event(id: Int, startMinutes: Long, stopMinutes: Long) = EpgEventEntry(
        eventId = id,
        channelId = id / 10,
        start = startMinutes * 60,
        stop = stopMinutes * 60,
        title = "Event $id",
    )
}
