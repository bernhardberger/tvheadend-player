package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.bannerFramePresented
import at.bernhardberger.tvhplayer.core.bannerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerStatusKind
import at.bernhardberger.tvhplayer.core.programmeChangeBannerDue
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.common.formatClock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The New design's Banner layer, programme-change rule and live status/info-bar data. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTrialBannerTest {
    @Test
    fun enteringWithTheNewDesignShowsTheBannerInsteadOfControls() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        assertTrue(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun theCurrentDesignNeverShowsTheBanner() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(false)
        assertTrue(state.controlsVisible)
        state.hideControls()
        state.onChannelTuneRequested()
        state.onProgrammeChanged()
        state.onBannerFramePresented(true)
        assertFalse(state.bannerVisible)
        assertTrue(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun bannerStaysWhileTuningAndHidesFiveSecondsAfterTheFirstFrame() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue("no frame yet: the Banner stays while tuning", state.bannerVisible)
        state.onBannerFramePresented(true)
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue(state.bannerVisible)
        advanceTimeBy(2L)
        runCurrent()
        assertFalse(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun zapWithHiddenControlsShowsTheBannerUntilTheNewChannelsFrame() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onBannerFramePresented(true)
        advanceTimeBy(6_000L)
        runCurrent()
        assertFalse(state.bannerVisible)

        state.onChannelTuneRequested()
        state.onBannerFramePresented(false)
        assertTrue(state.bannerVisible)
        assertFalse(state.controlsVisible)
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(state.bannerVisible)
        state.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse(state.bannerVisible)
        state.dispose()
    }

    @Test
    fun zapWithVisibleControlsKeepsTheControls() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.showControls()
        assertFalse(state.bannerVisible)
        state.onChannelTuneRequested()
        assertTrue(state.controlsVisible)
        assertFalse(state.bannerVisible)
        state.dispose()
    }

    @Test
    fun okAndPanelsReplaceTheBannerAndBackHidesIt() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.showControls()
        assertFalse(state.bannerVisible)
        assertTrue(state.controlsVisible)
        assertEquals(PlayerControlsEntry.TRAVEL, state.controlsEntry)

        state.hideControls()
        state.onChannelTuneRequested()
        state.openInfo()
        assertFalse(state.bannerVisible)

        state.closeInfo()
        state.hideControls()
        state.onChannelTuneRequested()
        state.showOptionsPage(PlaybackOptionsPage.ROOT)
        assertFalse(state.bannerVisible)

        state.closeOptions()
        state.hideControls()
        state.onChannelTuneRequested()
        state.hideBanner()
        assertFalse(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun aKeyBeforeTheDesignLoadsKeepsTheEntryControls() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.onKeyDown()
        state.enableBanner(true)
        assertFalse(state.bannerVisible)
        assertTrue(state.controlsVisible)
        state.dispose()
    }

    @Test
    fun failedTuneReplacesTheBannerWithTheCentreMessage() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onChannelUnavailable()
        assertFalse(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()

        // Current: a failed tune reveals the controls, as before.
        val current = LivePlayerLayerState(this, 5_000L)
        current.hideControls()
        current.onChannelUnavailable()
        assertTrue(current.controlsVisible)
        current.dispose()
    }

    @Test
    fun programmeChangeShowsTheBannerForFiveSecondsOnlyWithChromeHidden() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        state.onProgrammeChanged()
        assertTrue(state.bannerVisible)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse(state.bannerVisible)

        state.showControls()
        state.onProgrammeChanged()
        assertFalse(state.bannerVisible)
        state.hideControls()
        state.openInfo()
        state.onProgrammeChanged()
        assertFalse(state.bannerVisible)
        state.dispose()
    }

    @Test
    fun programmeChangeBannerIsDueOnlyAtTheLiveEdgeWithChromeHidden() {
        assertTrue(programmeChangeBannerDue(1L, 2L, chromeHidden = true, atLiveEdge = true, seekPreview = false))
        assertFalse(programmeChangeBannerDue(1L, 1L, chromeHidden = true, atLiveEdge = true, seekPreview = false))
        assertFalse(programmeChangeBannerDue(null, 2L, chromeHidden = true, atLiveEdge = true, seekPreview = false))
        assertFalse(programmeChangeBannerDue(1L, null, chromeHidden = true, atLiveEdge = true, seekPreview = false))
        assertFalse(programmeChangeBannerDue(1L, 2L, chromeHidden = false, atLiveEdge = true, seekPreview = false))
        assertFalse(programmeChangeBannerDue(1L, 2L, chromeHidden = true, atLiveEdge = false, seekPreview = false))
        assertFalse(programmeChangeBannerDue(1L, 2L, chromeHidden = true, atLiveEdge = true, seekPreview = true))
    }

    @Test
    fun okOnTheBannerRevealsTheControlsWithoutTogglingPause() {
        assertEquals(PlayerKeyAction.REVEAL_CONTROLS, bannerKeyAction(PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE, bannerVisible = true))
        // Hidden player without the Banner: OK still pauses, as before.
        assertEquals(PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE, bannerKeyAction(PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE, bannerVisible = false))
        for (other in PlayerKeyAction.entries - PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE) {
            assertEquals(other, bannerKeyAction(other, bannerVisible = true))
        }
    }

    @Test
    fun okJustAfterTheBannersTimeoutHideStillRevealsTheControlsWithoutPausing() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse("the Banner timed out", state.bannerVisible)
        assertTrue(state.bannerTakesOk)
        assertEquals("OK within the grace is still meant for the Banner", PlayerKeyAction.REVEAL_CONTROLS,
            bannerKeyAction(PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE, state.bannerTakesOk))
        advanceTimeBy(LIVE_PLAYER_BANNER_OK_GRACE_MS)
        runCurrent()
        assertFalse(state.bannerTakesOk)
        assertEquals("after the grace OK pauses as before", PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE,
            bannerKeyAction(PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE, state.bannerTakesOk))
        state.dispose()
    }

    @Test
    fun onlyTheTimeoutHideOpensTheOkGraceAndAnyLayerChangeEndsIt() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onBannerFramePresented(true)
        // Back on the Banner (or a stop) hides it without a grace.
        state.hideBanner()
        assertFalse(state.bannerTakesOk)
        state.onChannelTuneRequested()
        state.showControls()
        assertFalse("revealing the controls opens no grace", state.bannerTakesOk)
        state.hideControls()
        state.onChannelTuneRequested()
        state.openInfo()
        assertFalse("a panel opening opens no grace", state.bannerTakesOk)
        state.closeInfo()
        state.hideControls()

        val timedOut: suspend () -> Unit = {
            state.onChannelTuneRequested()
            advanceTimeBy(5_001L)
            runCurrent()
            assertFalse(state.bannerVisible)
            assertTrue(state.bannerTakesOk)
        }
        timedOut()
        state.showControls()
        assertFalse("a controls reveal ends the grace", state.bannerTakesOk)
        state.hideControls()
        timedOut()
        state.hideBanner()
        assertFalse("a stop ends the grace", state.bannerTakesOk)
        timedOut()
        state.showOptionsPage(PlaybackOptionsPage.ROOT)
        assertFalse("a panel ends the grace", state.bannerTakesOk)
        state.closeOptions()
        state.hideControls()
        timedOut()
        state.onChannelTuneRequested()
        state.onBannerFramePresented(false)
        assertTrue("a new tune shows the Banner again", state.bannerVisible)
        state.onChannelUnavailable()
        assertFalse("a failed tune's hide opens no grace", state.bannerTakesOk)
        state.dispose()
    }

    @Test
    fun liveStatusFollowsTuningBufferingPauseAndBehindLive() {
        assertNull(liveTrialStatus(AppTimeshiftState(), false, false, false, presented = false, unavailable = false))
        assertEquals(PlayerStatusKind.TUNING,
            liveTrialStatus(AppTimeshiftState(), false, true, false, false, false)?.kind)
        assertEquals(PlayerStatusKind.BUFFERING,
            liveTrialStatus(AppTimeshiftState(), false, false, true, true, false)?.kind)
        assertEquals(PlayerStatusKind.LIVE,
            liveTrialStatus(AppTimeshiftState(), false, false, false, true, false)?.kind)
        assertEquals(PlayerStatusKind.PAUSED,
            liveTrialStatus(AppTimeshiftState(), true, false, false, true, false)?.kind)
        val behind = liveTrialStatus(
            AppTimeshiftState(available = true, positionMs = 10_000L, liveEdgeMs = 130_000L), false, false, false, true, false,
        )
        assertEquals(PlayerStatusKind.BEHIND_LIVE, behind?.kind)
        assertEquals(120L, behind?.behindLiveSeconds)
        assertNull(liveTrialStatus(AppTimeshiftState(), false, true, true, true, unavailable = true))
    }

    @Test
    fun liveInfoBarCarriesTheDisplayedAndNextProgrammeAndOmitsMissingFacts() {
        val now = event(1, 3_600L, 7_200L, "Now", subtitle = "Part one")
        val next = event(2, 7_200L, 9_000L, "Later")
        val data = liveInfoBarData(101L, "ORF 1 HD", now, next, nextScheduled = true, nowSec = 5_000L, unavailableTitle = "n/a")
        assertEquals("101", data.channelNumber)
        assertEquals("Now", data.title)
        assertEquals("Part one", data.subtitle)
        assertEquals(37, data.remainingMinutes)
        assertEquals("${formatClock(7_200L)} Later", data.next)
        assertTrue(data.nextScheduled)
        assertNull(data.recordedDate)

        val bare = liveInfoBarData(null, "ORF 1 HD", null, null, nextScheduled = true, nowSec = 5_000L, unavailableTitle = "n/a")
        assertEquals("", bare.channelNumber)
        assertEquals("n/a", bare.title)
        assertEquals("", bare.timeRange)
        assertNull(bare.metadata)
        assertNull(bare.remainingMinutes)
        assertNull(bare.next)
        assertFalse(bare.nextScheduled)
    }

    @Test
    fun onlyAVisibleFrameOrAPositivelyAudioOnlyServicePresentsTheBannerFrame() {
        assertTrue(bannerFramePresented(tuneConfirmed = true, videoFrameVisible = true, playing = false, audioOnly = false))
        assertTrue("audio-only: playing is the picture", bannerFramePresented(true, videoFrameVisible = false, playing = true, audioOnly = true))
        assertFalse("missing or cleared diagnostics never prove audio-only",
            bannerFramePresented(true, videoFrameVisible = false, playing = true, audioOnly = false))
        assertFalse(bannerFramePresented(true, videoFrameVisible = false, playing = false, audioOnly = true))
        assertFalse("not this tune", bannerFramePresented(false, videoFrameVisible = true, playing = true, audioOnly = true))
    }

    @Test
    fun aStopCancelsTheBannerAndItsTimerSoNoStaleBannerReturns() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onBannerFramePresented(true)
        advanceTimeBy(3_000L)
        runCurrent()
        // The screen stops: the Banner goes and its timer with it.
        state.hideBanner()
        state.onBannerFramePresented(false)
        assertFalse(state.bannerVisible)
        // Return and retune: a fresh Banner waits for the new frame; no old timer cuts it short.
        state.onChannelTuneRequested()
        assertTrue(state.bannerVisible)
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue("no stale timer: the Banner waits for this tune's frame", state.bannerVisible)
        state.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse(state.bannerVisible)
        assertFalse(state.controlsVisible)
        state.dispose()
    }

    private fun event(id: Long, start: Long, stop: Long, title: String, subtitle: String? = null) = EpgEvent.create(
        id = EventId(id),
        channelId = ChannelId(1L),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = title,
        subtitle = subtitle,
    )
}
