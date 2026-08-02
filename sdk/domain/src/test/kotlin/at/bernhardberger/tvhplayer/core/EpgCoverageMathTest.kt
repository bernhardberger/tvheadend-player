package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun topUpEligibilityUsesInclusiveCooldownAndTreatsEqualHorizonAsCovered() {
        val attempted = EpgCoverage().afterAttempt(attemptedAtSec = 1_000L)

        assertFalse(
            attempted.needsTopUp(
                wantedTo = 20_000L,
                nowSec = 1_599L,
                cooldownSec = 600L,
            )
        )
        assertTrue(
            attempted.needsTopUp(
                wantedTo = 20_000L,
                nowSec = 1_600L,
                cooldownSec = 600L,
            )
        )

        val exactlyCovered = attempted.afterSuccessfulFetch(
            targetTo = 20_000L,
            attemptedAtSec = 1_000L,
        )
        assertFalse(
            exactlyCovered.needsTopUp(
                wantedTo = 20_000L,
                nowSec = 1_600L,
                cooldownSec = 600L,
            )
        )
    }

    @Test
    fun successfulEmptyFetchAdvancesOnlyTheQueriedHorizon() {
        val coverage = EpgCoverage().afterSuccessfulFetch(
            targetTo = 20_000L,
            attemptedAtSec = 1_000L,
        )

        assertTrue(coverage.eventCoverage.isEmpty)
        assertEquals(Long.MAX_VALUE, coverage.coveredFrom)
        assertEquals(0L, coverage.coveredTo)
        assertEquals(20_000L, coverage.knownTo)
        assertFalse(
            coverage.needsTopUp(
                wantedTo = 20_000L,
                nowSec = 1_001L,
                cooldownSec = 600L,
            )
        )
        assertTrue(
            coverage.needsTopUp(
                wantedTo = 21_000L,
                nowSec = 1_600L,
                cooldownSec = 600L,
            )
        )
    }

    @Test
    fun observedAndSuccessfullyQueriedHorizonsAreMonotonic() {
        val coverage = EpgCoverage()
            .includingObservedCoverage(Coverage(from = 100L, to = 400L))
            .includingObservedCoverage(Coverage(from = 200L, to = 300L))
            .afterSuccessfulFetch(targetTo = 20_000L, attemptedAtSec = 1_000L)
            .afterSuccessfulFetch(targetTo = 10_000L, attemptedAtSec = 1_100L)

        assertEquals(100L, coverage.coveredFrom)
        assertEquals(400L, coverage.coveredTo)
        assertEquals(20_000L, coverage.queriedTo)
        assertEquals(1_100L, coverage.lastAttemptSec)
    }

    @Test
    fun authoritativeRetainedCoverageCanShrinkAndResetWithoutErasingQueryHistory() {
        val queried = EpgCoverage()
            .includingObservedCoverage(Coverage(from = 100L, to = 400L))
            .afterSuccessfulFetch(targetTo = 20_000L, attemptedAtSec = 1_000L)

        val shrunk = queried.withRetainedCoverage(Coverage(from = 200L, to = 300L))
        assertEquals(200L, shrunk.coveredFrom)
        assertEquals(300L, shrunk.coveredTo)
        assertEquals(20_000L, shrunk.queriedTo)

        val emptied = shrunk.withRetainedCoverage(Coverage.Empty)
        assertTrue(emptied.eventCoverage.isEmpty)
        assertEquals(Long.MAX_VALUE, emptied.coveredFrom)
        assertEquals(0L, emptied.coveredTo)
        assertEquals(20_000L, emptied.knownTo)
    }

    @Test
    fun nextTargetWarmsThenChunksAndClampsAtTheMaximumHorizon() {
        val cold = EpgCoverage()
        assertEquals(
            4_000L,
            cold.nextTargetTo(
                desiredWarmTo = 4_000L,
                desiredMinTo = 20_000L,
                desiredMaxTo = 24_000L,
                chunkSec = 4_000L,
            )
        )

        val warmBoundary = cold.afterSuccessfulFetch(
            targetTo = 4_000L,
            attemptedAtSec = 1L,
        )
        assertEquals(
            8_000L,
            warmBoundary.nextTargetTo(
                desiredWarmTo = 4_000L,
                desiredMinTo = 20_000L,
                desiredMaxTo = 24_000L,
                chunkSec = 4_000L,
            )
        )

        val nearCap = cold.afterSuccessfulFetch(
            targetTo = 23_000L,
            attemptedAtSec = 1L,
        )
        assertEquals(
            24_000L,
            nearCap.nextTargetTo(
                desiredWarmTo = 4_000L,
                desiredMinTo = 25_000L,
                desiredMaxTo = 24_000L,
                chunkSec = 4_000L,
            )
        )

        val minimumBoundary = cold.afterSuccessfulFetch(
            targetTo = 20_000L,
            attemptedAtSec = 1L,
        )
        assertNull(
            minimumBoundary.nextTargetTo(
                desiredWarmTo = 4_000L,
                desiredMinTo = 20_000L,
                desiredMaxTo = 24_000L,
                chunkSec = 4_000L,
            )
        )
    }
}
