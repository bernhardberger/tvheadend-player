package at.bernhardberger.tvhplayer.ui.components

import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelPlaybackIndicatorTest {
    private val channel = ChannelId(1)
    private val target = AppPlaybackTarget.Live(channel)

    @Test fun onlyPlayAndPauseArePlayingItemIdentity() {
        ChannelPlaybackIndicator.entries.forEach {
            assertEquals(it == ChannelPlaybackIndicator.PLAYING || it == ChannelPlaybackIndicator.PAUSED, it.isPlayingItem)
        }
        listOf(AppPlaybackState.Starting,
            AppPlaybackState.Recovering(at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason.LIVE_ENDED, 0L), AppPlaybackState.Idle,
            AppPlaybackState.Finished, AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)).forEach {
            assertEquals(false, channelPlaybackIndicator(channel, target, it, true).isPlayingItem)
        }
    }

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

    @Test fun railShowsWhatPlaysUntilItEndsFailsOrBrowseStopsIt() {
        val recording = DvrEntry.create(DvrEntryId(7), channelId = channel, title = "Northline Tonight",
            channelName = "Old name")
        val observation = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(
                Channel.create(channel, name = "Ridge Earth HD", number = 1, icon = ChannelIcon.ImageCache(ArtworkId(1)))))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(recording))),
        )
        val recordingTarget = AppPlaybackTarget.Recording(recording.id)
        fun rail(target: AppPlaybackTarget?, state: AppPlaybackState, warm: DvrEntryId? = null, stop: Boolean = false) =
            railNowPlaying(target, state, observation, warm, stop)

        assertEquals(RailNowPlaying(target, channel, 1, "Ridge Earth HD", ChannelIcon.ImageCache(ArtworkId(1)), null, reconnecting = false),
            rail(target, AppPlaybackState.Playing))
        for (state in listOf(AppPlaybackState.Starting, AppPlaybackState.Buffering)) {
            assertEquals(false, rail(target, state)?.reconnecting)
        }
        assertEquals(true, rail(target, AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L))?.reconnecting)
        for (state in listOf(AppPlaybackState.Idle, AppPlaybackState.Finished,
            AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER))) {
            assertEquals(null, rail(target, state))
        }
        assertEquals(null, rail(null, AppPlaybackState.Playing))
        // Stop while browsing hides it as browse begins, before the stop completes.
        assertEquals(null, rail(target, AppPlaybackState.Playing, stop = true))
        assertEquals(null, rail(AppPlaybackTarget.Live(ChannelId(9)), AppPlaybackState.Playing))

        // A recording counts only while it is the warm one; its channel comes from the line-up.
        assertEquals(null, rail(recordingTarget, AppPlaybackState.Playing))
        assertEquals(RailNowPlaying(recordingTarget, channel, 1, "Ridge Earth HD", ChannelIcon.ImageCache(ArtworkId(1)), "Northline Tonight",
            reconnecting = false), rail(recordingTarget, AppPlaybackState.Playing, warm = recording.id))
    }

    @Test fun railStateOrdersReconnectTuneAndPauseBeforeWhereTheViewerIs() {
        val live = RailNowPlaying(target, channel, 1, "Ridge Earth HD", null, null, reconnecting = false)
        val recorded = live.copy(target = AppPlaybackTarget.Recording(DvrEntryId(7)), recordingTitle = "Northline Tonight")
        fun status(nowPlaying: RailNowPlaying, indicator: ChannelPlaybackIndicator, behind: Boolean = false) =
            railPlaybackStatus(nowPlaying, indicator, behind)
        assertEquals(RailPlaybackStatus.LIVE, status(live, ChannelPlaybackIndicator.PLAYING))
        assertEquals(RailPlaybackStatus.BEHIND_LIVE, status(live, ChannelPlaybackIndicator.PLAYING, behind = true))
        assertEquals(RailPlaybackStatus.PAUSED, status(live, ChannelPlaybackIndicator.PAUSED, behind = true))
        assertEquals(RailPlaybackStatus.TUNING, status(live, ChannelPlaybackIndicator.TUNING))
        assertEquals(RailPlaybackStatus.RECONNECTING,
            status(live.copy(reconnecting = true), ChannelPlaybackIndicator.PAUSED))
        assertEquals(RailPlaybackStatus.RECORDED, status(recorded, ChannelPlaybackIndicator.PLAYING))
        assertEquals(RailPlaybackStatus.PAUSED, status(recorded, ChannelPlaybackIndicator.PAUSED))
    }

    @Test fun behindLiveCountsWholeMinutesOnceLocated() {
        assertEquals(12L, railBehindLiveMinutes(1_000L, 1_000L + 12 * 60 + 59))
        assertEquals(null, railBehindLiveMinutes(1_000L, 1_059L))
        assertEquals(null, railBehindLiveMinutes(null, 1_000L))
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
