package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelPlaybackIndicatorTest {
    private val channel = ChannelId(1)
    private val target = AppPlaybackTarget.Live(channel)

    @Test fun playPauseTuneConfirmationAndFailureFollowCurrentOwner() {
        fun indicator(state: AppPlaybackState, intent: Boolean = true) =
            channelPlaybackIndicator(channel, target, state, intent)
        assertEquals(ChannelPlaybackIndicator.PLAYING, indicator(AppPlaybackState.Playing))
        assertEquals(ChannelPlaybackIndicator.PAUSED, indicator(AppPlaybackState.Playing, false))
        assertEquals(ChannelPlaybackIndicator.TUNING, indicator(AppPlaybackState.Starting, false))
        assertEquals(ChannelPlaybackIndicator.PLAYING, indicator(AppPlaybackState.Playing))
        assertEquals(ChannelPlaybackIndicator.NONE, indicator(AppPlaybackState.Idle))
        assertEquals(ChannelPlaybackIndicator.NONE, indicator(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)))
        assertEquals(ChannelPlaybackIndicator.NONE, indicator(AppPlaybackState.Finished))
    }

    @Test fun presentedStallUsesIntentAndNeverRetunes() {
        assertEquals(ChannelPlaybackIndicator.PLAYING,
            channelPlaybackIndicator(channel, target, AppPlaybackState.Buffering, true))
        assertEquals(ChannelPlaybackIndicator.PAUSED,
            channelPlaybackIndicator(channel, target, AppPlaybackState.Buffering, false))
    }

    @Test fun onlyTheOwnedLiveTargetCanShowAMarker() {
        for (state in listOf(AppPlaybackState.Starting, AppPlaybackState.Playing, AppPlaybackState.Buffering)) {
            assertEquals(ChannelPlaybackIndicator.NONE,
                channelPlaybackIndicator(ChannelId(2), target, state, true))
            assertEquals(ChannelPlaybackIndicator.NONE,
                channelPlaybackIndicator(channel, null, state, true))
            assertEquals(ChannelPlaybackIndicator.NONE,
                channelPlaybackIndicator(channel, AppPlaybackTarget.Recording(DvrEntryId(1)), state, true))
        }
    }
}
