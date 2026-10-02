package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MainStartupLoadingFeedbackTest {
    @Test
    fun cachedWaitUsesExactGraceWithoutVisualPromotion() {
        val cached = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA)
        assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(cached, 399L))
        assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(cached, 400L))
        assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(cached, 1_999L))
        assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(cached, 2_000L))
        assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(cached, 30_000L))
    }

    @Test
    fun localUnknownDoesNotFlashFullSynchronization() {
        val preparing = MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING)
        val connecting = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        val tuning = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
        for (unknown in listOf(preparing, connecting, tuning)) {
            assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(unknown, 0L))
            assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(unknown, 399L))
            assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(unknown, 400L))
            assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(unknown, 2_000L))
        }
    }

    @Test
    fun uncachedSynchronizationHasTheSameGraceAsEveryBlockingStage() {
        val syncing = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA)
        assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(syncing, 0L))
        assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(syncing, 399L))
        assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(syncing, 400L))
    }

    @Test
    fun readyContentAndRecoveryNeverShowLoadingRegardlessOfElapsedTime() {
        for (presentation in listOf(
            MainStartupPresentation.Inactive,
            MainStartupPresentation.Enter(ApplianceLaunchRequest(1)),
            MainStartupPresentation.Actionable(MainStartupMessageKind.RETRYABLE_FAILURE, listOf(MainStartupActionId.RETRY)),
        )) {
            assertEquals(MainStartupLoadingFeedback.HIDDEN, mainStartupLoadingFeedback(presentation, 10_000L))
        }
    }

    @Test
    fun localResolutionAndLaunchRequestShareOneContinuousWait() {
        val timing = MainStartupLoadingTiming()
        assertEquals(0L, timing.observe(true, null, 100L))
        assertEquals(399L, timing.observe(true, 1L, 499L))
        assertEquals(400L, timing.observe(true, 1L, 500L))
        assertEquals(2_000L, timing.elapsedMillis(true, 1L, 2_100L))
    }

    @Test
    fun connectingSyncReconnectAndTuningKeepOneContinuousWait() {
        val timing = MainStartupLoadingTiming()
        timing.observe(true, 1L, 0L)
        for ((index, stage) in listOf(MainStartupMessageKind.CONNECTING, MainStartupMessageKind.SYNCING_METADATA, MainStartupMessageKind.RECONNECTING, MainStartupMessageKind.SYNCING_METADATA, MainStartupMessageKind.STARTING_TELEVISION).withIndex()) {
            assertEquals((index + 1) * 400L,
                timing.observe(true, 1L, (index + 1) * 400L))
            assertEquals(MainStartupLoadingFeedback.WAITING, mainStartupLoadingFeedback(MainStartupPresentation.Passive(stage), (index + 1) * 400L))
        }
    }

    @Test
    fun recoveryThenRetryOfSameRequestStartsFreshGrace() {
        val timing = MainStartupLoadingTiming()
        timing.observe(true, 1L, 0L)
        assertEquals(2_000L, timing.elapsedMillis(true, 1L, 2_000L))
        timing.observe(false, 1L, 2_001L)
        assertEquals(0L, timing.observe(true, 1L, 3_000L))
        assertEquals(399L, timing.elapsedMillis(true, 1L, 3_399L))
    }

    @Test
    fun retryAndProfileReplacementCannotCarryTimingFromPreviousRequest() {
        val timing = MainStartupLoadingTiming()
        timing.observe(true, 1L, 0L)
        assertEquals(0L, timing.elapsedMillis(true, 2L, 3_000L))
        assertEquals(0L, timing.observe(true, 2L, 3_000L))
        assertEquals(399L, timing.elapsedMillis(true, 2L, 3_399L))
        assertEquals(0L, timing.observe(false, null, 4_000L))
        assertEquals(0L, timing.elapsedMillis(true, 3L, 5_000L))
        assertEquals(0L, timing.observe(true, 3L, 5_000L))
    }

    @Test
    fun navigationReadinessOrBackRetiresPendingFeedback() {
        val timing = MainStartupLoadingTiming()
        timing.observe(true, 1L, 0L)
        assertEquals(0L, timing.observe(false, 1L, 399L))
        assertEquals(0L, timing.elapsedMillis(false, 1L, 2_000L))
        assertEquals(0L, timing.elapsedMillis(true, 1L, 2_001L))
        assertEquals(0L, timing.observe(true, 1L, 2_001L))
    }
}
