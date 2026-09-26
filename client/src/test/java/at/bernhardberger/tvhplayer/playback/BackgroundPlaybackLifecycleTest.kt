package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.*
import org.junit.Test

class BackgroundPlaybackLifecycleTest {
    private val live = AppPlaybackTarget.Live(ChannelId(1))

    @Test fun eligibilityMatrixAndResumeIntent() {
        for (timeshift in listOf(false, true)) for (minutes in listOf(0, 20))
            for (playing in listOf(false, true)) for (interactive in listOf(false, true))
                for (serverPaused in listOf(false, true)) {
                    val lifecycle = ForegroundPlaybackLifecycle()
                    val action = lifecycle.onBackgrounded(live, 7, playing, timeshift, serverPaused, minutes.minutes, interactive, 1_000)
                    if (timeshift && minutes > 0 && interactive) {
                        assertEquals(ForegroundPlaybackAction.KeepLive(7, 1_201_000), action)
                        assertEquals(ForegroundPlaybackAction.ResumeKeptLive(7, playing && !serverPaused),
                            lifecycle.onForegrounded(live, 7, 2_000))
                    } else {
                        assertEquals(ForegroundPlaybackAction.StopLive, action)
                        assertEquals(ForegroundPlaybackAction.ResumeLive(ChannelId(1)), lifecycle.onForegrounded(null, null, 2_000))
                    }
                }
    }

    @Test fun expiryStandbyLossAndCommandFailureReleaseOnceAndRetuneOnce() {
        for (notice in listOf(null, BackgroundPlaybackNotice.LIMIT_EXPIRED, BackgroundPlaybackNotice.TUNER_LOST)) {
            val lifecycle = kept()
            assertEquals(ForegroundPlaybackAction.StopLive, lifecycle.releaseKept(7, notice))
            assertFalse(lifecycle.isKeeping(7))
            assertEquals(ForegroundPlaybackAction.None, lifecycle.releaseKept(7, notice))
            assertEquals(ForegroundPlaybackAction.ResumeLive(ChannelId(1), notice), lifecycle.onForegrounded(null, null))
            assertEquals(ForegroundPlaybackAction.None, lifecycle.onForegrounded(null, null))
        }
    }

    @Test fun deadlineAlsoAppliesWhenForegroundBeatsTimerDelivery() {
        val lifecycle = kept()
        assertEquals(ForegroundPlaybackAction.ResumeLive(ChannelId(1), BackgroundPlaybackNotice.LIMIT_EXPIRED),
            lifecycle.onForegrounded(live, 7, 1_200_000))
        assertEquals(ForegroundPlaybackAction.None, lifecycle.releaseKept(7, BackgroundPlaybackNotice.LIMIT_EXPIRED))
    }

    @Test fun repeatedBackgroundDoesNotReplaceOriginalResumeIntentOrDeadline() {
        val lifecycle = kept()
        assertEquals(ForegroundPlaybackAction.None, lifecycle.onBackgrounded(live, 7, false, true, true, 10.minutes, true, 100))
        assertEquals(ForegroundPlaybackAction.ResumeKeptLive(7, true), lifecycle.onForegrounded(live, 7, 600_001))
        assertEquals(ForegroundPlaybackAction.None, lifecycle.onForegrounded(live, 7, 600_002))
    }

    @Test fun unlimitedKeepLimitSaturatesDeadlineInsteadOfExpiring() {
        val lifecycle = ForegroundPlaybackLifecycle()
        assertEquals(ForegroundPlaybackAction.KeepLive(7, Long.MAX_VALUE),
            lifecycle.onBackgrounded(live, 7, true, true, false, Duration.INFINITE, true, 5_000))
        assertEquals(ForegroundPlaybackAction.ResumeKeptLive(7, true), lifecycle.onForegrounded(live, 7, 10_000_000))
    }

    @Test fun staleEpochAndExplicitStopCannotReleaseOrResumeReplacement() {
        val lifecycle = kept()
        assertEquals(ForegroundPlaybackAction.None, lifecycle.releaseKept(6, BackgroundPlaybackNotice.TUNER_LOST))
        assertTrue(lifecycle.isKeeping(7))
        assertEquals(ForegroundPlaybackAction.None, lifecycle.onForegrounded(live, 8))
        val stopped = kept()
        stopped.onExplicitStop()
        assertEquals(ForegroundPlaybackAction.None, stopped.releaseKept(7))
        assertEquals(ForegroundPlaybackAction.None, stopped.onForegrounded(null, null))
    }

    @Test fun replacementClearsKeptTargetAndPendingNotice() {
        val lifecycle = kept()
        lifecycle.releaseKept(7, BackgroundPlaybackNotice.TUNER_LOST)
        val replacement = AppPlaybackTarget.Recording(DvrEntryId(4))
        assertEquals(ForegroundPlaybackAction.PauseRecording, lifecycle.onTargetStarted(replacement, 8))
        assertEquals(ForegroundPlaybackAction.None, lifecycle.releaseKept(7))
        assertEquals(ForegroundPlaybackAction.ResumeRecording, lifecycle.onForegrounded(replacement, 8))
    }

    @Test fun newerIntentThanTheBackgroundedChannelSkipsResumeAndReleasesKeptTuner() {
        val stopped = ForegroundPlaybackLifecycle()
        assertEquals(ForegroundPlaybackAction.StopLive,
            stopped.onBackgrounded(live, 7, true, false, false, 20.minutes, true, 0, targetIntent = 3))
        assertEquals(ForegroundPlaybackAction.None, stopped.onForegrounded(null, null, 1, latestIntent = 4))
        val lost = ForegroundPlaybackLifecycle()
        lost.onBackgrounded(live, 7, true, recoveryPending = true, targetIntent = 3)
        assertEquals(ForegroundPlaybackAction.None, lost.onForegrounded(null, null, 1, latestIntent = 4))
        val kept = ForegroundPlaybackLifecycle()
        kept.onBackgrounded(live, 7, true, true, false, 20.minutes, true, 0, targetIntent = 3)
        assertEquals(ForegroundPlaybackAction.StopLive, kept.onForegrounded(live, 7, 1, latestIntent = 4))
        val served = ForegroundPlaybackLifecycle()
        served.onBackgrounded(live, 7, true, true, false, 20.minutes, true, 0, targetIntent = 4)
        assertEquals(ForegroundPlaybackAction.ResumeKeptLive(7, true), served.onForegrounded(live, 7, 1, latestIntent = 4))
        val startedInBackground = ForegroundPlaybackLifecycle()
        startedInBackground.onBackgrounded(null, null, false)
        assertEquals(ForegroundPlaybackAction.StopLive, startedInBackground.onTargetStarted(live, 8, targetIntent = 5))
        assertEquals(ForegroundPlaybackAction.ResumeLive(ChannelId(1)),
            startedInBackground.onForegrounded(null, null, 1, latestIntent = 5))
    }

    private fun kept() = ForegroundPlaybackLifecycle().apply {
        onBackgrounded(live, 7, true, true, false, 20.minutes, true, 0)
    }
}
