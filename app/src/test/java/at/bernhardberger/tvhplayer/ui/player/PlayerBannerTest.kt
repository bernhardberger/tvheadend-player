package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.bannerFramePresented
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

/** The Banner layer, programme-change rule and live status/info-bar data. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerBannerTest {
    @Test
    fun zapWithHiddenControlsShowsTheBannerUntilTheChannelsFrame() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.chrome.onBannerFramePresented(true)
        advanceTimeBy(6_000L)
        runCurrent()
        assertFalse(state.chrome.bannerVisible)

        state.onChannelTuneRequested()
        state.chrome.onBannerFramePresented(false)
        assertTrue(state.chrome.bannerVisible)
        assertFalse(state.chrome.controlsVisible)
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(state.chrome.bannerVisible)
        state.chrome.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse(state.chrome.bannerVisible)
        state.dispose()
    }

    @Test
    fun zapWithVisibleControlsKeepsTheControls() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        assertFalse(state.chrome.bannerVisible)
        state.onChannelTuneRequested()
        assertTrue(state.chrome.controlsVisible)
        assertFalse(state.chrome.bannerVisible)
        state.dispose()
    }

    @Test
    fun okAndPanelsReplaceTheBannerAndBackHidesIt() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.showControls()
        assertFalse(state.chrome.bannerVisible)
        assertTrue(state.chrome.controlsVisible)
        // The controls take the Banner's content over; with the Banner gone a reveal travels in.
        assertEquals(PlayerControlsEntry.FROM_BANNER, state.chrome.controlsEntry)
        state.chrome.hideControls()
        state.showControls()
        assertEquals(PlayerControlsEntry.TRAVEL, state.chrome.controlsEntry)

        state.chrome.hideControls()
        state.onChannelTuneRequested()
        state.openInfo()
        assertFalse(state.chrome.bannerVisible)

        state.closeInfo()
        state.chrome.hideControls()
        state.onChannelTuneRequested()
        state.showOptionsPage(PlaybackOptionsPage.ROOT)
        assertFalse(state.chrome.bannerVisible)

        state.closeOptions()
        state.chrome.hideControls()
        state.onChannelTuneRequested()
        state.chrome.hideBanner()
        assertFalse(state.chrome.bannerVisible)
        assertFalse(state.chrome.controlsVisible)
        state.dispose()
    }

    @Test
    fun failedTuneReplacesTheBannerWithTheCentreMessage() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.onChannelUnavailable()
        assertFalse(state.chrome.bannerVisible)
        assertFalse(state.chrome.controlsVisible)
        state.dispose()
    }

    @Test
    fun programmeChangeShowsTheBannerForFiveSecondsOnlyWithChromeHidden() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.chrome.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        state.onProgrammeChanged()
        assertTrue(state.chrome.bannerVisible)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse(state.chrome.bannerVisible)

        state.showControls()
        state.onProgrammeChanged()
        assertFalse(state.chrome.bannerVisible)
        state.chrome.hideControls()
        state.openInfo()
        state.onProgrammeChanged()
        assertFalse(state.chrome.bannerVisible)
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
    fun liveBehindFollowsTuningTimeshiftAndKnownTiming() {
        // A tune and a fresh live start land at the live edge before their timing is known.
        assertEquals(0L, liveBehindMs(AppTimeshiftState(), tuning = true))
        assertEquals(0L, liveBehindMs(AppTimeshiftState(available = true, timingKnown = false), tuning = true))
        assertEquals(0L, liveBehindMs(AppTimeshiftState(available = true, timingKnown = false), liveStart = true))
        // Without timeshift playback is at the live edge.
        assertEquals(0L, liveBehindMs(AppTimeshiftState()))
        // Timeshift whose timing is not known yet has no distance.
        assertNull(liveBehindMs(AppTimeshiftState(available = true, timingKnown = false)))
        assertEquals(120_000L, liveBehindMs(AppTimeshiftState(available = true, positionMs = 10_000L, liveEdgeMs = 130_000L)))
        // Within the live-edge tolerance playback is at the edge.
        assertEquals(0L, liveBehindMs(AppTimeshiftState(available = true, positionMs = 126_000L, liveEdgeMs = 130_000L)))
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
        state.chrome.onBannerFramePresented(true)
        advanceTimeBy(3_000L)
        runCurrent()
        // The screen stops: the Banner goes and its timer with it.
        state.chrome.hideBanner()
        state.chrome.onBannerFramePresented(false)
        assertFalse(state.chrome.bannerVisible)
        // Return and retune: a fresh Banner waits for the new frame; no old timer cuts it short.
        state.onChannelTuneRequested()
        assertTrue(state.chrome.bannerVisible)
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue("no stale timer: the Banner waits for this tune's frame", state.chrome.bannerVisible)
        state.chrome.onBannerFramePresented(true)
        advanceTimeBy(5_001L)
        runCurrent()
        assertFalse(state.chrome.bannerVisible)
        assertFalse(state.chrome.controlsVisible)
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
