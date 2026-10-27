package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.LivePauseState
import org.junit.Assert.*
import org.junit.Test

class DrawerTransportPolicyTest {
    private val live = AppPlaybackTarget.Live(ChannelId(1))
    private val recording = AppPlaybackTarget.Recording(DvrEntryId(1))
    private val ready = LivePauseState(LivePauseAvailability.READY)

    @Test fun liveUsesPendingIntentAndPausedSeekRatherThanTemporaryPlayerState() {
        val starting = LivePauseState(LivePauseAvailability.STARTING, selectionPending = true)
        assertEquals(false, drawerControlPaused(live, live, starting, false))
        assertEquals(false, drawerTogglePlayWhenReady(live, live, starting, false))
        val pending = starting.copy(pending = true)
        assertEquals(true, drawerControlPaused(live, live, pending, true))
        assertEquals(true, drawerTogglePlayWhenReady(live, live, pending, true))
        assertEquals(true, drawerControlPaused(live, live, ready.copy(pausedSeekRefresh = true), true))
        for (playing in listOf(false, true)) {
            assertEquals(!playing, drawerControlPaused(live, live, ready, playing))
            assertEquals(!playing, drawerTogglePlayWhenReady(live, live, ready, playing))
        }
    }

    @Test fun recordingUsesPlayerIntentRegardlessOfLivePendingPause() {
        for (playing in listOf(false, true)) {
            val pending = ready.copy(pending = true, selectionPending = true)
            assertEquals(!playing, drawerControlPaused(recording, recording, pending, playing))
            assertEquals(!playing, drawerTogglePlayWhenReady(recording, recording, pending, playing))
        }
    }

    @Test fun absentAndStaleTargetsCannotToggleOrStop() {
        for ((target, active) in listOf(null to live, live to null, live to recording,
            recording to live, live to AppPlaybackTarget.Live(ChannelId(2)),
            recording to AppPlaybackTarget.Recording(DvrEntryId(2)))) {
            assertNull(drawerControlPaused(target, active, ready, true))
            assertNull(drawerTogglePlayWhenReady(target, active, ready, true))
            assertFalse(shouldStopDrawerPlayback(target, active, false))
        }
    }

    @Test fun stopAcceptsOwnedTargetsButNeverOverlapsAnUnfinishedStop() {
        for (target in listOf(live, recording)) {
            assertTrue(shouldStopDrawerPlayback(target, target, false))
            assertFalse(shouldStopDrawerPlayback(target, target, true))
        }
    }
}
