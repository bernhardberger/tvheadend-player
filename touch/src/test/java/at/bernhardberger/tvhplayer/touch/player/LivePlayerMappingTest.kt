package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(UnstableApi::class, TvheadendTestingApi::class)
class LivePlayerMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val shown = guide.first.id
    private val other = guide.second.id

    @Test
    fun statusFollowsTheRuntimeAsOnTv() {
        data class Case(
            val name: String,
            val state: AppPlaybackState,
            val expected: LivePlayerStatus,
            val connected: Boolean = true,
            val playing: Boolean = true,
            val issue: SubscriptionIssue? = null,
            val serverStopped: Boolean = false,
            val requestFailed: Boolean = false,
            val notice: BackgroundPlaybackNotice? = null,
        )
        val recovering = AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, retryDelayMillis = 1_000)
        val noTuner = AppPlaybackState.Failed(
            AppPlaybackFailureReason.OTHER, subscriptionIssue = SubscriptionIssue.NO_FREE_ADAPTER,
        )
        val cases = listOf(
            Case("picture shown", AppPlaybackState.Playing, LivePlayerStatus.Playing),
            Case("stall of a shown picture", AppPlaybackState.Buffering, LivePlayerStatus.Playing),
            Case("starting", AppPlaybackState.Starting, LivePlayerStatus.Tuning),
            Case("nothing asked yet", AppPlaybackState.Idle, LivePlayerStatus.Tuning, playing = false),
            Case("previous channel still on", AppPlaybackState.Playing, LivePlayerStatus.Tuning, playing = false),
            Case("another channel's issue", AppPlaybackState.Playing, LivePlayerStatus.Tuning, playing = false,
                issue = SubscriptionIssue.NO_FREE_ADAPTER),
            Case("connection lost", AppPlaybackState.Playing, LivePlayerStatus.Reconnecting(null), connected = false),
            Case("runtime recovering", recovering, LivePlayerStatus.Reconnecting(null)),
            Case("no free tuner", noTuner, LivePlayerStatus.Unavailable(AppErrorCondition.NO_FREE_ADAPTER),
                playing = false),
            Case("issue on the shown channel", AppPlaybackState.Playing,
                LivePlayerStatus.Unavailable(AppErrorCondition.NO_FREE_ADAPTER),
                issue = SubscriptionIssue.NO_FREE_ADAPTER),
            Case("start rejected", AppPlaybackState.Idle,
                LivePlayerStatus.Unavailable(AppErrorCondition.STREAM_STOPPED),
                playing = false, requestFailed = true),
            Case("server stopped the stream", AppPlaybackState.Playing,
                LivePlayerStatus.Unavailable(AppErrorCondition.STREAM_STOPPED), serverStopped = true),
            Case("this device's player failed",
                AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER, deviceFault = true),
                LivePlayerStatus.Unavailable(AppErrorCondition.CHANNEL_PLAYBACK_FAILED), playing = false),
            Case("keep limit ran out", AppPlaybackState.Finished, LivePlayerStatus.PauseEnded, playing = false,
                notice = BackgroundPlaybackNotice.LIMIT_EXPIRED),
        )
        cases.forEach { case ->
            val status = liveStatus(
                connected = case.connected, state = case.state, issue = case.issue, serverStopped = case.serverStopped,
                playingId = if (case.playing) shown else other, shownId = shown,
                requestFailed = case.requestFailed, notice = case.notice,
            )
            assertEquals(case.name, case.expected, status)
        }
    }

    @Test
    fun timelineSitsAtTheLiveEdgeOfTheRunningProgram() {
        val start = 72_000L
        val hour = 3_600L
        assertEquals(
            LiveTimeline(LocalTime.of(20, 0), 3_600, bufferStart = null, position = 900, live = 900),
            liveTimeline(start, start + hour, start + 900, ZoneOffset.UTC),
        )
        assertEquals(0, liveTimeline(start, start + hour, start, ZoneOffset.UTC)?.live)
        // Outside its program, or a program without length, has no line.
        assertEquals(null, liveTimeline(start, start + hour, start - 1, ZoneOffset.UTC))
        assertEquals(null, liveTimeline(start, start + hour, start + hour, ZoneOffset.UTC))
        assertEquals(null, liveTimeline(start, start, start, ZoneOffset.UTC))
    }

    @Test
    fun onlyALiveTargetThatHasNotEndedIsPlaying() {
        val live = AppPlaybackTarget.Live(shown)
        assertEquals(shown, playingChannelId(live, AppPlaybackState.Starting))
        assertEquals(shown, playingChannelId(live, AppPlaybackState.Playing))
        assertEquals(null, playingChannelId(live, AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)))
        assertEquals(null, playingChannelId(live, AppPlaybackState.Finished))
        assertEquals(null, playingChannelId(AppPlaybackTarget.Recording(DvrEntryId(1)), AppPlaybackState.Playing))
        assertEquals(null, playingChannelId(null, AppPlaybackState.Idle))
    }

    @Test
    fun faceCarriesTheShownChannelAndOffersOnlyWhatIsWired() {
        val observation = SessionObservation.create(epgState = EpgRepositoryState.Current(guide.snapshot))
        val channels = listOf(guide.first, guide.second)
        fun face(id: at.bernhardberger.tvheadend.sdk.core.ChannelId, known: Boolean = true) = livePlayerState(
            id,
            ChannelScopeState(
                ChannelBrowsingScope(
                    allChannels = if (known) channels else emptyList(),
                    visibleChannels = if (known) channels else emptyList(),
                    tags = emptyList(), allChannelsVisible = true, activeTagId = null,
                ),
                channelCatalogCurrent = known,
            ),
            observation, ConnectionUiState.Ready, degraded = false, LivePlayerStatus.Playing, guide.now, ZoneOffset.UTC,
        )

        val playing = face(shown)
        assertEquals("Channel One", playing.channel.name)
        assertEquals(guide.programs[0], playing.channel.now?.title)
        assertEquals(1_800, playing.timeline?.position)
        assertEquals(emptySet<PlayerControl>(), playing.offered)
        // The list below the picture marks the shown channel as the one playing.
        assertEquals(listOf(true, false), playing.channels.rows.map { it.playback != null })
        // A channel without guide data has no line; one the scope doesn't know yet has no name.
        assertEquals(null, face(other).timeline)
        assertEquals("", face(shown, known = false).channel.name)
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
