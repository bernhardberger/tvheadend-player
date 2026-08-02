package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgBatchSelectionPolicyTest {
    @Test
    fun emptyChannelListProducesNoSelectionAndZeroProgress() {
        assertEquals(
            emptyList<Int>(),
            selectEpgTopUpChannelIds(
                orderedChannelIds = emptyList(),
                coverageByChannelId = emptyMap(),
                inFlightChannelIds = emptySet(),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = 4,
            ),
        )
        assertEquals(
            EpgWarmupProgress(completed = 0, total = 0),
            epgWarmupProgress(
                orderedChannelIds = emptyList(),
                coverageByChannelId = emptyMap(),
                wantedTo = 1_000L,
                nowSec = 500L,
            ),
        )
    }

    @Test
    fun zeroLimitIsEmptyAndNegativeLimitIsRejected() {
        val arguments = { limit: Int ->
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(1),
                coverageByChannelId = emptyMap(),
                inFlightChannelIds = emptySet(),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = limit,
            )
        }

        assertEquals(emptyList<Int>(), arguments(0))
        assertThrows(IllegalArgumentException::class.java) { arguments(-1) }
    }

    @Test
    fun missingCoverageIsEligibleWithoutMutatingTheInputMap() {
        val coverage = mutableMapOf<Int, EpgCoverage>()

        assertEquals(
            listOf(7),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(7),
                coverageByChannelId = coverage,
                inFlightChannelIds = emptySet(),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = 1,
            ),
        )
        assertTrue(coverage.isEmpty())
    }

    @Test
    fun inFlightChannelsAreExcludedBeforeEligibilityAndRanking() {
        assertEquals(
            listOf(2),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(1, 2),
                coverageByChannelId = mapOf(
                    1 to coverage(coveredTo = 10L),
                    2 to coverage(coveredTo = 20L),
                ),
                inFlightChannelIds = setOf(1),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = 2,
            ),
        )
    }

    @Test
    fun successfulEmptyQueryHorizonControlsTopUpEligibility() {
        val queried = EpgCoverage().afterSuccessfulFetch(
            targetTo = 1_000L,
            attemptedAtSec = 100L,
        )

        assertEquals(
            emptyList<Int>(),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(1),
                coverageByChannelId = mapOf(1 to queried),
                inFlightChannelIds = emptySet(),
                wantedTo = 1_000L,
                nowSec = 100L,
                cooldownSec = 0L,
                limit = 1,
            ),
        )
        assertEquals(
            listOf(1),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(1),
                coverageByChannelId = mapOf(1 to queried),
                inFlightChannelIds = emptySet(),
                wantedTo = 1_001L,
                nowSec = 100L,
                cooldownSec = 0L,
                limit = 1,
            ),
        )
    }

    @Test
    fun candidatesAreRankedByCoveredToRatherThanKnownTo() {
        val lowCoverageHighQuery = coverage(coveredTo = 100L, queriedTo = 900L)
        val highCoverageLowQuery = coverage(coveredTo = 200L, queriedTo = 300L)

        assertEquals(
            listOf(1, 2),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(2, 1),
                coverageByChannelId = mapOf(
                    1 to lowCoverageHighQuery,
                    2 to highCoverageLowQuery,
                ),
                inFlightChannelIds = emptySet(),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = 2,
            ),
        )
    }

    @Test
    fun coveredToTiesRetainSuppliedChannelOrder() {
        assertEquals(
            listOf(30, 10, 20),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(30, 10, 20),
                coverageByChannelId = mapOf(
                    10 to coverage(coveredTo = 100L),
                    20 to coverage(coveredTo = 100L),
                    30 to coverage(coveredTo = 100L),
                ),
                inFlightChannelIds = emptySet(),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = 3,
            ),
        )
    }

    @Test
    fun limitIsAppliedAfterFilteringAndRanking() {
        assertEquals(
            listOf(4),
            selectEpgTopUpChannelIds(
                orderedChannelIds = listOf(1, 2, 3, 4),
                coverageByChannelId = mapOf(
                    1 to coverage(coveredTo = 1L),
                    2 to coverage(coveredTo = 1_000L),
                    3 to coverage(coveredTo = 300L),
                    4 to coverage(coveredTo = 100L),
                ),
                inFlightChannelIds = setOf(1),
                wantedTo = 1_000L,
                nowSec = 500L,
                cooldownSec = 0L,
                limit = 1,
            ),
        )
    }

    @Test
    fun cooldownIsIneligibleOneSecondBeforeAndEligibleAtTheBoundary() {
        val attempted = EpgCoverage(lastAttemptSec = 100L)

        assertEquals(
            emptyList<Int>(),
            selectionForSingle(coverage = attempted, nowSec = 699L, cooldownSec = 600L),
        )
        assertEquals(
            listOf(1),
            selectionForSingle(coverage = attempted, nowSec = 700L, cooldownSec = 600L),
        )
    }

    @Test
    fun warmupTreatsEqualHorizonAsCompleteAndOneSecondBelowAsIncomplete() {
        val progress = epgWarmupProgress(
            orderedChannelIds = listOf(1, 2),
            coverageByChannelId = mapOf(
                1 to EpgCoverage(queriedTo = 1_000L),
                2 to EpgCoverage(queriedTo = 999L),
            ),
            wantedTo = 1_000L,
            nowSec = 500L,
        )

        assertEquals(EpgWarmupProgress(completed = 1, total = 2), progress)
    }

    @Test
    fun successfulEmptyQueryCompletesWarmupThroughQueriedTo() {
        val progress = epgWarmupProgress(
            orderedChannelIds = listOf(1),
            coverageByChannelId = mapOf(
                1 to EpgCoverage().afterSuccessfulFetch(
                    targetTo = 1_000L,
                    attemptedAtSec = 500L,
                ),
            ),
            wantedTo = 1_000L,
            nowSec = 500L,
        )

        assertEquals(EpgWarmupProgress(completed = 1, total = 1), progress)
    }

    @Test
    fun missingCoverageIsIncompleteForWarmup() {
        assertEquals(
            EpgWarmupProgress(completed = 0, total = 1),
            epgWarmupProgress(
                orderedChannelIds = listOf(1),
                coverageByChannelId = emptyMap(),
                wantedTo = 1_000L,
                nowSec = 500L,
            ),
        )
    }

    @Test
    fun futureAttemptWithZeroCooldownPreservesClockRegressionBehavior() {
        val futureAttempt = EpgCoverage(lastAttemptSec = 501L)

        assertEquals(
            emptyList<Int>(),
            selectionForSingle(coverage = futureAttempt, nowSec = 500L, cooldownSec = 0L),
        )
        assertEquals(
            EpgWarmupProgress(completed = 1, total = 1),
            epgWarmupProgress(
                orderedChannelIds = listOf(1),
                coverageByChannelId = mapOf(1 to futureAttempt),
                wantedTo = 1_000L,
                nowSec = 500L,
            ),
        )
    }

    private fun selectionForSingle(
        coverage: EpgCoverage,
        nowSec: Long,
        cooldownSec: Long,
    ) = selectEpgTopUpChannelIds(
        orderedChannelIds = listOf(1),
        coverageByChannelId = mapOf(1 to coverage),
        inFlightChannelIds = emptySet(),
        wantedTo = 1_000L,
        nowSec = nowSec,
        cooldownSec = cooldownSec,
        limit = 1,
    )

    private fun coverage(
        coveredTo: Long,
        queriedTo: Long = 0L,
    ) = EpgCoverage(
        eventCoverage = Coverage(from = 0L, to = coveredTo),
        queriedTo = queriedTo,
    )
}
