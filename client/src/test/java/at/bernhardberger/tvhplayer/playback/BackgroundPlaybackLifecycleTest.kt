package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import org.junit.Assert.*
import org.junit.Test

class BackgroundPlaybackLifecycleTest {
    private val live = AppPlaybackTarget.Live(ChannelId(1))

    @Test fun eligibilityMatrixAndResumeIntent() {
        for (timeshift in listOf(false, true)) for (minutes in listOf(0, 20))
            for (playing in listOf(false, true)) for (interactive in listOf(false, true))
                for (serverPaused in listOf(false, true)) {
                    val lifecycle = ForegroundPlaybackLifecycle()
                    val action = lifecycle.onBackgrounded(live, 7, playing, timeshift, serverPaused, minutes, interactive, 1_000)
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
        assertEquals(ForegroundPlaybackAction.None, lifecycle.onBackgrounded(live, 7, false, true, true, 10, true, 100))
        assertEquals(ForegroundPlaybackAction.ResumeKeptLive(7, true), lifecycle.onForegrounded(live, 7, 600_001))
        assertEquals(ForegroundPlaybackAction.None, lifecycle.onForegrounded(live, 7, 600_002))
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

    private fun kept() = ForegroundPlaybackLifecycle().apply {
        onBackgrounded(live, 7, true, true, false, 20, true, 0)
    }
}
