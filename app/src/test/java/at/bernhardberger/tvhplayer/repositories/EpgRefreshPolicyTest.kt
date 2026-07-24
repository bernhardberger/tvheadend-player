package at.bernhardberger.tvhplayer.repositories

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgRefreshPolicyTest {

    @Test
    fun successfulEmptyQuery_coversTheRequestedHorizon() {
        val coverage = EpgCoverage()

        coverage.recordSuccessfulFetch(targetTo = 20_000, attemptedAtSec = 1_000)

        assertTrue(coverage.coveredTo == 0L)
        assertFalse(
            coverage.needsTopUp(
                wantedTo = 20_000,
                nowSec = 1_001,
                cooldownSec = 600,
            )
        )
    }

    @Test
    fun queriedHorizon_expiresAsTimeAdvances() {
        val coverage = EpgCoverage()
        coverage.recordSuccessfulFetch(targetTo = 20_000, attemptedAtSec = 1_000)

        assertTrue(
            coverage.needsTopUp(
                wantedTo = 21_000,
                nowSec = 1_601,
                cooldownSec = 600,
            )
        )
    }

    @Test
    fun failedAttempt_observesCooldownWithoutClaimingCoverage() {
        val coverage = EpgCoverage()

        coverage.recordAttempt(attemptedAtSec = 1_000)

        assertFalse(
            coverage.needsTopUp(
                wantedTo = 20_000,
                nowSec = 1_100,
                cooldownSec = 600,
            )
        )
        assertTrue(
            coverage.needsTopUp(
                wantedTo = 20_000,
                nowSec = 1_601,
                cooldownSec = 600,
            )
        )
    }
}
