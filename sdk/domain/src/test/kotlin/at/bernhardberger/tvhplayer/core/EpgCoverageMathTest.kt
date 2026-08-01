package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgCoverageMathTest {

    private fun event(start: Long, stop: Long) =
        EpgEventEntry(eventId = start.toInt(), channelId = 1, start = start, stop = stop, title = "x")

    @Test
    fun emptyList_producesInvertedWindow_soWorkerRefetches() {
        // Core of the "No EPG after long uptime" fix: a drained channel must report
        // empty coverage so the worker re-fetches it instead of believing it's full.
        val cov = coverageForEvents(emptyList())
        assertTrue(cov.isEmpty)
        assertEquals(Long.MAX_VALUE, cov.from)
        assertEquals(0L, cov.to)
    }

    @Test
    fun coverage_isDerivedFromActualEvents_notMonotonicMax() {
        val cov = coverageForEvents(
            listOf(event(100, 200), event(200, 300), event(300, 400))
        )
        assertEquals(100L, cov.from)
        assertEquals(400L, cov.to)
        assertTrue(!cov.isEmpty)
    }

    @Test
    fun coverage_handlesUnsortedInput() {
        val cov = coverageForEvents(
            listOf(event(300, 400), event(100, 200), event(200, 300))
        )
        assertEquals(100L, cov.from)
        assertEquals(400L, cov.to)
    }

    @Test
    fun coverage_shrinks_whenEventsTrimmedAway() {
        // Before: covered to 400. After trimming leaves only the early event, coverage
        // must drop to 200 (a monotonic max would have kept 400 and starved the worker).
        val full = coverageForEvents(listOf(event(100, 200), event(300, 400)))
        assertEquals(400L, full.to)

        val trimmed = coverageForEvents(listOf(event(100, 200)))
        assertEquals(200L, trimmed.to)
        assertTrue(trimmed.to < full.to)
    }
}
