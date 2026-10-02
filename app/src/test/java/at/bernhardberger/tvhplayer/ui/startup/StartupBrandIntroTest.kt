package at.bernhardberger.tvhplayer.ui.startup

import android.animation.ValueAnimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StartupBrandIntroTest {
    @Test fun cachedColdConnectingWaitUsesProductionPresentationAndGraceAdmission() {
        val connection = at.bernhardberger.tvhplayer.core.ConnectionUiState.Connecting
        val presentation = at.bernhardberger.tvhplayer.core.mainStartupPresentation(
            startupState = at.bernhardberger.tvhplayer.core.MainStartupState.Ready(
                server = at.bernhardberger.tvhplayer.settings.ServerSettings(host = ""),
                autoStartPlayback = true,
            ),
            launchState = at.bernhardberger.tvhplayer.core.ApplianceLaunchState.Pending(
                at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest(1L)),
            connectionState = connection,
            currentChannelReadiness = at.bernhardberger.tvhplayer.core.CurrentChannelReadiness.Browsable(
                listOf(at.bernhardberger.tvheadend.sdk.core.Channel.create(
                    at.bernhardberger.tvheadend.sdk.core.ChannelId(1L)))),
        )
        val intro = StartupBrandIntro(at.bernhardberger.tvhplayer.core.MainStartupProcessEntry().claim(false))
        intro.observe(connection)
        intro.resumed(true); intro.focused(true); intro.entranceReady()
        for (elapsed in listOf(0L, 399L, 400L)) {
            val feedback = at.bernhardberger.tvhplayer.core.mainStartupLoadingFeedback(presentation, elapsed)
            intro.passive(mainStartupBrandPassiveHint(presentation),
                feedback == at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback.WAITING)
            assertEquals(elapsed >= 400L, intro.running)
        }
        intro.frame(900f)
        assertEquals(900f, intro.millis)
        intro.passive(mainStartupBrandPassiveHint(at.bernhardberger.tvhplayer.core.MainStartupPresentation.Inactive))
        assertFalse(intro.running)
        assertEquals(900f, intro.outgoingMillis)
    }

    @Test fun metadataPublicationAndPassiveTuningKeepAdmittedMotionUntilHandoff() {
        val intro = StartupBrandIntro(true)
        start(intro)
        intro.frame(900f)
        intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Ready)
        intro.passive(true, gracePassed = true)
        assertTrue(intro.running)
        assertEquals(900f, intro.millis)
        intro.passive(false)
        assertFalse(intro.running)
        assertEquals(900f, intro.outgoingMillis)
    }
    @Test fun activityEntranceCompletionWithoutSplashCallbackStartsAtOpeningFrameAfterFocus() {
        val intro = StartupBrandIntro(eligible = true)
        assertEquals(0f, intro.millis, 0f)
        intro.resumed(true)
        intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Connecting)
        intro.passive(true, gracePassed = true)
        assertFalse(intro.running)
        assertEquals(startupBrandFrame(0f), startupBrandFrame(intro.millis))
        // Matches the observed Activity entrance callback followed by window focus.
        intro.entranceReady()
        assertFalse(intro.running)
        intro.focused(true)
        assertTrue(intro.running)
        assertEquals(0f, intro.millis, 0f)
        intro.frame(570f)
        assertEquals(570f, intro.millis, 0f)
        intro.finish()
    }

    @Test fun entranceAndFocusCallbackOrderAndDuplicatesNeverRewindOrReplay() {
        listOf(false, true).forEach { entranceBeforeFocus ->
            val intro = StartupBrandIntro(eligible = true)
            intro.resumed(true)
            intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Connecting)
            intro.passive(true, gracePassed = true)
            if (entranceBeforeFocus) intro.entranceReady()
            intro.focused(true)
            if (!entranceBeforeFocus) intro.entranceReady()
            intro.frame(570f)
            // Duplicate entrance-complete callbacks must not restart the clock.
            repeat(2) { intro.entranceReady() }
            assertEquals(570f, intro.millis, 0f)
            intro.frame(StartupBrandDurationMillis)
            intro.entranceReady()
            assertFalse(intro.running)
            assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
        }
    }

    @Test fun interruptionBeforeEntranceSettlesMotionAndLateCallbacksCannotRearm() {
        val exits: List<(StartupBrandIntro) -> Unit> = listOf(
            { it.passive(false) }, { it.resumed(false) }, { it.focused(false) }, { it.finish() },
        )
        exits.forEach { exit ->
            val intro = StartupBrandIntro(eligible = true)
            intro.resumed(true)
            intro.passive(true)
            intro.focused(true)
            exit(intro)
            assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
            start(intro)
            intro.entranceReady()
            assertFalse(intro.running)
        }
    }

    @Test fun admittedOwnerCannotReplay() {
        repeat(2) {
            val intro = StartupBrandIntro(eligible = true)
            try {
                start(intro)
                assertTrue(intro.running)
                assertEquals(0f, intro.millis, 0f)
                intro.frame(570f)
                assertEquals(570f, intro.millis, 0f)
                intro.frame(StartupBrandDurationMillis)
                // Same-Activity resume and a later passive wait cannot rearm this owner.
                intro.resumed(false)
                intro.focused(false)
                intro.passive(false)
                start(intro)
                intro.frame(210f)
                assertFalse(intro.running)
                assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
            } finally {
                intro.finish()
            }
        }
    }

    @Test fun freshAlreadyReadyOwnerCannotAnimateOnALaterPassiveWait() {
        val intro = StartupBrandIntro(eligible = true)
        try {
            intro.passive(false)
            start(intro)
            assertFalse(intro.running)
            assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
        } finally {
            intro.finish()
        }
    }

    @Test fun restoredIneligibleEntryIsSettledAndNeverStartsAtEntrance() {
        val intro = StartupBrandIntro(eligible = false)
        assertEquals(startupBrandFrame(StartupBrandDurationMillis), startupBrandFrame(intro.millis))
        start(intro)
        assertFalse(intro.running)
    }

    @Test fun reducedMotionIsSettledBeforeAndAfterEntrance() {
        val previous = ReflectionHelpers.callStaticMethod<Float>(ValueAnimator::class.java, "getDurationScale")
        fun durationScale(value: Float) = ReflectionHelpers.callStaticMethod<Unit>(
            ValueAnimator::class.java, "setDurationScale",
            ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType, value),
        )
        try {
            durationScale(0f)
            val intro = StartupBrandIntro(eligible = true)
            assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
            start(intro)
            assertFalse(intro.running)
            durationScale(1f)
            intro.entranceReady()
            assertFalse(intro.running)
        } finally {
            durationScale(previous)
        }
    }

    @Test fun motionSettlesAtItsDeadlineAndCannotRestart() {
        val intro = StartupBrandIntro(eligible = true)
        start(intro)
        assertTrue(intro.running)
        intro.frame(StartupBrandDurationMillis)
        assertFalse(intro.running)
        assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
        intro.passive(true)
        assertFalse(intro.running)
    }

    @Test fun everyRuntimeExitEndsMotionWithoutReplay() {
        val exits: List<(StartupBrandIntro) -> Unit> = listOf(
            { it.passive(false) }, { it.resumed(false) }, { it.focused(false) }, { it.finish() },
        )
        exits.forEach { exit ->
            val intro = StartupBrandIntro(eligible = true)
            start(intro)
            intro.frame(570f)
            exit(intro)
            assertFalse(intro.running)
            assertEquals(StartupBrandDurationMillis, intro.millis, 0f)
            start(intro)
            assertFalse(intro.running)
        }
    }

    private fun start(intro: StartupBrandIntro) {
        intro.observe(at.bernhardberger.tvhplayer.core.ConnectionUiState.Connecting)
        intro.passive(true, gracePassed = true)
        intro.resumed(true)
        intro.focused(true)
        intro.entranceReady()
    }
}
