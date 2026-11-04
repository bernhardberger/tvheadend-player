package at.bernhardberger.tvhplayer.ui.screens.settings

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackInterruptionTest {
    private val live = AppPlaybackTarget.Live(ChannelId(1))
    private val recording = AppPlaybackTarget.Recording(DvrEntryId(1))

    @Test
    fun onlyWhatIsStartingOrPlayingIsInterrupted() {
        for (state in listOf(AppPlaybackState.Starting, AppPlaybackState.Playing, AppPlaybackState.Buffering)) {
            assertEquals(PlaybackInterruption.LIVE, playbackInterruption(live, state))
            assertEquals(PlaybackInterruption.RECORDING, playbackInterruption(recording, state))
        }
    }

    @Test
    fun nothingPlayingSwitchesWithoutAsking() {
        val failed = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
        for (state in listOf(AppPlaybackState.Idle, AppPlaybackState.Finished, failed)) {
            assertEquals(PlaybackInterruption.NONE, playbackInterruption(live, state))
            assertEquals(PlaybackInterruption.NONE, playbackInterruption(recording, state))
        }
        assertEquals(PlaybackInterruption.NONE, playbackInterruption(null, AppPlaybackState.Playing))
    }
}
