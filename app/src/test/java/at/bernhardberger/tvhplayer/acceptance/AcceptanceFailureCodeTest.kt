package at.bernhardberger.tvhplayer.acceptance

import org.junit.Assert.assertEquals
import org.junit.Test

class AcceptanceFailureCodeTest {
    @Test
    fun `finds a typed failure across aggregated test and cleanup failures`() {
        val failures = listOf(
            IllegalStateException("cleanup failed"),
            AssertionError("ACCEPTANCE_PLAYBACK_FAILED"),
        )

        assertEquals("ACCEPTANCE_PLAYBACK_FAILED", acceptanceFailureCode(failures))
    }

    @Test
    fun `finds a typed failure in suppressed and causal failures`() {
        val root = IllegalStateException("outer").apply {
            addSuppressed(IllegalArgumentException("ACCEPTANCE_SURFACE_RELEASE_FAILED"))
        }
        val wrapped = RuntimeException("wrapper", root)

        assertEquals("ACCEPTANCE_SURFACE_RELEASE_FAILED", acceptanceFailureCode(listOf(wrapped)))
    }

    @Test
    fun `returns a bounded typed fallback without exposing exception text`() {
        val failure = IllegalStateException("sensitive runtime detail")

        assertEquals("ACCEPTANCE_UNCLASSIFIED_FAILURE", acceptanceFailureCode(listOf(failure)))
    }
}
