package at.bernhardberger.tvhplayer.ui.startup

import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupBrandTimelineTest {
    @Test fun creativeCuesOverlapAndHoldTheSettledFrame() {
        val early = startupBrandFrame(0f)
        assertEquals(0f, early.trace, 0f)
        assertEquals(0f, early.stroke, 0f)
        assertEquals(0f, early.segmentFill, 0f)
        assertEquals(0f, early.play, 0f)
        assertEquals(0f, early.ring, 0f)
        assertEquals(8f, early.gap, 0f)
        assertEquals(0f, early.wordmark, 0f)
        assertEquals(early, startupBrandFrame(250f))
        assertEquals(0.5f, startupBrandFrame(350f).stroke, 0.0001f)
        assertEquals(0.875f, startupBrandFrame(565f).trace, 0.0001f)
        assertEquals(0.5f, startupBrandFrame(715f).segmentFill, 0.0001f)
        assertEquals(0.875f, startupBrandFrame(670f).wordmark, 0.0001f)
        assertEquals(1f, startupBrandFrame(940f).wordmark, 0f)
        assertEquals(1f, startupBrandFrame(910f).gap, 0.0001f)
        assertEquals(0.5f, startupBrandFrame(1105f).ring, 0.0001f)
        assertEquals(0.5f, startupBrandFrame(1150f).stroke, 0.0001f)
        assertEquals(0.5f, startupBrandFrame(1165f).play, 0.0001f)
        val settled = startupBrandFrame(1480f)
        assertEquals(0f, settled.gap, 0f)
        assertEquals(1f, settled.playScale, 0.0001f)
        assertEquals(0f, settled.playDy, 0f)
        assertEquals(settled, startupBrandFrame(StartupBrandDurationMillis))
        assertEquals(settled, startupBrandFrame(2000f))
        assertEquals(early, startupBrandFrame(-100f))
    }

    @Test fun startsOnlyAfterEntranceReadyForegroundFocusAndPassiveSurface() {
        val policy = StartupBrandEligibility(true)
        policy.update(false, true, true, true, true)
        assertFalse(policy.running)
        policy.update(true, false, true, true, true)
        assertFalse(policy.running)
        policy.update(true, true, false, true, true)
        assertFalse(policy.running)
        policy.update(true, true, true, null, true)
        assertFalse(policy.running)
        policy.update(true, true, true, true, true)
        assertTrue(policy.running)
        // Bootstrap -> ready is still passive: no finish/restart at that branch boundary.
        policy.update(true, true, true, true, true)
        assertTrue(policy.running)
        assertFalse(policy.finished)
    }

    @Test fun noWaitOrReducedMotionSkipsPermanentlyEvenBeforeWindowFocus() {
        listOf(false to true, true to false).forEach { (passive, motion) ->
            val policy = StartupBrandEligibility(true)
            policy.update(true, true, false, passive, motion)
            assertTrue(policy.finished)
            policy.update(true, true, true, true, true)
            assertFalse(policy.running)
        }
    }

    @Test fun readinessRecoveryOrReducedMotionBeforeEntranceSkipsWithoutWaitingForCallback() {
        listOf(false to true, true to false).forEach { (passive, motion) ->
            val policy = StartupBrandEligibility(true)
            policy.update(false, true, false, passive, motion)
            assertTrue(policy.finished)
            policy.update(true, true, true, true, true)
            assertFalse(policy.running)
        }
    }

    @Test fun readinessErrorBackgroundFocusLossAndReducedMotionTerminateWithoutReplay() {
        listOf(
            listOf(false, true, true, true),
            listOf(true, false, true, true),
            listOf(true, true, false, true),
            listOf(true, true, true, false),
        ).forEach { (resumed, focused, passive, motion) ->
            val policy = StartupBrandEligibility(true)
            policy.update(true, true, true, true, true)
            policy.update(true, resumed, focused, passive, motion)
            assertTrue(policy.finished)
            policy.update(true, true, true, true, true)
            assertFalse(policy.running)
        }
    }

    @Test fun backDisposalOrCompletionCannotRearmAnIntro() {
        val policy = StartupBrandEligibility(true)
        policy.finish()
        policy.update(true, true, true, true, true)
        assertFalse(policy.running)
        assertTrue(policy.finished)
    }

    @Test fun passiveLifetimeEndsOnReadinessWithoutOwningAdmission() {
        val preparing = MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING)
        val connecting = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        val fullSync = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA)
        val tuning = MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
        val cold = StartupBrandEligibility(true)
        for (presentation in listOf(preparing, connecting, fullSync, tuning)) {
            assertEquals(true, mainStartupBrandPassiveHint(presentation))
            cold.update(true, true, true, mainStartupBrandPassiveHint(presentation), true)
            assertTrue(cold.running)
            assertFalse(cold.finished)
        }
        cold.update(true, true, true, mainStartupBrandPassiveHint(MainStartupPresentation.Inactive), true)
        assertTrue(cold.finished)
        cold.update(true, true, true, mainStartupBrandPassiveHint(preparing), true)
        assertFalse(cold.running)
    }
}
