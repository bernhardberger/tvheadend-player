package at.bernhardberger.tvhplayer.acceptance

import at.bernhardberger.tvhplayer.playback.AppLivePlaybackIssue
import at.bernhardberger.tvhplayer.playback.AppPlaybackCommandResult
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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

    @Test
    fun `stage wrapper replaces an unexpected failure with only its bounded code`() {
        val failure = assertThrows(AssertionError::class.java) {
            withAcceptanceStage("ACCEPTANCE_STAGE_TARGET_ADMISSION_FAILED") {
                throw IllegalStateException("sensitive runtime detail")
            }
        }

        assertEquals("ACCEPTANCE_STAGE_TARGET_ADMISSION_FAILED", failure.message)
        assertEquals(null, failure.cause)
    }

    @Test
    fun `stage wrapper preserves an existing typed assertion`() {
        val expected = AssertionError("ACCEPTANCE_LIVE_TUNE_REJECTED")

        val failure = assertThrows(AssertionError::class.java) {
            withAcceptanceStage("ACCEPTANCE_STAGE_TARGET_ADMISSION_FAILED") {
                throw expected
            }
        }

        assertSame(expected, failure)
    }

    @Test
    fun `stage wrapper preserves coroutine cancellation`() {
        val expected = CancellationException("cancelled")

        val failure = assertThrows(CancellationException::class.java) {
            withAcceptanceStage("ACCEPTANCE_STAGE_PLAYER_READINESS_FAILED") {
                throw expected
            }
        }

        assertSame(expected, failure)
    }

    @Test
    fun `subscription issues map to stable secret safe evidence`() {
        assertEquals(
            mapOf(
                AppLivePlaybackIssue.INVALID_TARGET to "ACCEPTANCE_SUBSCRIPTION_INVALID_TARGET",
                AppLivePlaybackIssue.NO_FREE_ADAPTER to "ACCEPTANCE_SUBSCRIPTION_NO_FREE_ADAPTER",
                AppLivePlaybackIssue.MUX_NOT_ENABLED to "ACCEPTANCE_SUBSCRIPTION_MUX_NOT_ENABLED",
                AppLivePlaybackIssue.TUNING_FAILED to "ACCEPTANCE_SUBSCRIPTION_TUNING_FAILED",
                AppLivePlaybackIssue.BAD_SIGNAL to "ACCEPTANCE_SUBSCRIPTION_BAD_SIGNAL",
                AppLivePlaybackIssue.SCRAMBLED to "ACCEPTANCE_SUBSCRIPTION_SCRAMBLED",
                AppLivePlaybackIssue.OVERRIDDEN to "ACCEPTANCE_SUBSCRIPTION_OVERRIDDEN",
                AppLivePlaybackIssue.ACCESS_DENIED to "ACCEPTANCE_SUBSCRIPTION_ACCESS_DENIED",
                AppLivePlaybackIssue.CONNECTION_LIMIT to "ACCEPTANCE_SUBSCRIPTION_CONNECTION_LIMIT",
                AppLivePlaybackIssue.WEAK_STREAM to "ACCEPTANCE_SUBSCRIPTION_WEAK_STREAM",
                AppLivePlaybackIssue.NO_DISK_SPACE to "ACCEPTANCE_SUBSCRIPTION_NO_DISK_SPACE",
                AppLivePlaybackIssue.UNKNOWN to "ACCEPTANCE_SUBSCRIPTION_UNKNOWN",
                AppLivePlaybackIssue.NO_INPUT to "ACCEPTANCE_SUBSCRIPTION_NO_INPUT",
            ),
            AppLivePlaybackIssue.entries.associateWith(::acceptanceSubscriptionFailureCode),
        )
    }

    @Test
    fun `target admission failures map released coordinator outcomes without detail`() {
        assertEquals(
            mapOf(
                AppPlaybackCommandResult.NOT_RUNNING to "ACCEPTANCE_TARGET_NOT_RUNNING",
                AppPlaybackCommandResult.SHUT_DOWN to "ACCEPTANCE_TARGET_SHUT_DOWN",
                AppPlaybackCommandResult.NOT_READY to "ACCEPTANCE_TARGET_NOT_READY",
                AppPlaybackCommandResult.TARGET_UNAVAILABLE to "ACCEPTANCE_TARGET_UNAVAILABLE",
                AppPlaybackCommandResult.PLAYER_UNAVAILABLE to "ACCEPTANCE_TARGET_PLAYER_UNAVAILABLE",
            ),
            listOf(
                AppPlaybackCommandResult.NOT_RUNNING,
                AppPlaybackCommandResult.SHUT_DOWN,
                AppPlaybackCommandResult.NOT_READY,
                AppPlaybackCommandResult.TARGET_UNAVAILABLE,
                AppPlaybackCommandResult.PLAYER_UNAVAILABLE,
            ).associateWith(::acceptanceTargetFailureCode),
        )
        assertEquals(
            "ACCEPTANCE_TARGET_ADMISSION_REJECTED",
            acceptanceTargetFailureCode(AppPlaybackCommandResult.REJECTED),
        )
    }
}
