package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.StreamProfile as SdkStreamProfile
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.HumanTrackLabel
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.ChannelsTexts
import at.bernhardberger.tvhplayer.touch.guide.GuideClock
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.LocalTime
import java.time.ZoneOffset
import java.lang.reflect.Proxy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
@kotlin.OptIn(TvheadendTestingApi::class)
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
            Case("requested channel recovered after its rejected start", AppPlaybackState.Playing,
                LivePlayerStatus.Playing, requestFailed = true),
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
    fun barFollowsTheTimeshiftSampleByTheTvProgramWindow() {
        data class Case(val name: String, val sample: AppTimeshiftState, val expected: LiveTimeline?, val now: Instant = live)
        val b = LocalTime.of(20, 0)
        val cases = listOf(
            Case("at the live edge", timeshift(50, 90, 90.minutes), LiveTimeline(b, 3_600, 0, 1_800, 1_800)),
            Case("within the live-edge tolerance", timeshift(50, 90, 90.minutes - 3.seconds),
                LiveTimeline(b, 3_600, 0, 1_800, 1_800, behind = 0)),
            Case("paused while live moves on", timeshift(50, 92, 90.minutes, edge = live + 2.minutes),
                LiveTimeline(b, 3_600, 0, 1_800, 1_920, behind = 120)),
            Case("behind and playing", timeshift(50, 90, 80.minutes, readerBehind = 10.minutes),
                LiveTimeline(b, 3_600, 0, 1_200, 1_800, behind = 600)),
            Case("buffer starts after the program", timeshift(70, 90, 80.minutes),
                LiveTimeline(b, 3_600, 600, 1_200, 1_800, behind = 600)),
            Case("behind into the previous program", timeshift(50, 90, 55.minutes),
                LiveTimeline(LocalTime.of(19, 0), 3_600, 3_000, 3_300, 3_600, behind = 2_100, liveEdge = false)),
            Case("no timeshift: the program on air", AppTimeshiftState(),
                LiveTimeline(b, 3_600, bufferStart = null, position = 1_800, live = 1_800)),
            Case("no timeshift and nothing on air", AppTimeshiftState(), null, now = live + 3.hours),
        )
        cases.forEach { case ->
            val mapped = liveTimeline(case.sample, case.now.epochSeconds, ZoneOffset.UTC, ::eventAt)
            assertEquals(case.name, case.expected, mapped?.copy(programmeStartSec = null, segment = null))
        }
    }

    @Test
    fun faceNamesTheProgramTheViewerIsIn() {
        data class Case(val name: String, val sample: AppTimeshiftState, val now: String?, val next: String?)
        val cases = listOf(
            Case("at the live edge", timeshift(50, 90, 90.minutes), "Program 2", null),
            Case("behind into the previous program", timeshift(50, 90, 55.minutes), "Program 1", "Program 2"),
            Case("no timeshift: the program on air", AppTimeshiftState(), "Program 2", null),
        )
        cases.forEach { case ->
            val shown = shownPrograms(case.sample, liveStart = false, live, ::eventAt) {
                schedule.nextEvent(ChannelId(1), it)
            }
            assertEquals(case.name, case.now to case.next, shown.now?.title to shown.next?.title)
        }
    }

    @Test
    fun skipsAndDragsMoveByTheTvRules() {
        assertEquals(TimeshiftMove.By(-10_000), skipMove(timeshift(50, 90, 80.minutes), -10_000))
        assertEquals(TimeshiftMove.By(30_000), skipMove(timeshift(50, 90, 80.minutes), 30_000))
        assertEquals(TimeshiftMove.None, skipMove(timeshift(50, 90, 50.minutes), -10_000))
        assertEquals(TimeshiftMove.None, skipMove(timeshift(50, 90, 90.minutes), 30_000))
        // A forward skip that reaches the live edge goes live rather than stopping just short of it.
        assertEquals(TimeshiftMove.GoLive, skipMove(timeshift(50, 90, 89.minutes + 40.seconds), 30_000))
        assertEquals(TimeshiftMove.GoLive, skipMove(timeshift(50, 90, 89.minutes + 27.seconds), 30_000))
        assertEquals(TimeshiftMove.By(30_000), skipMove(timeshift(50, 90, 89.minutes + 20.seconds), 30_000))
        assertEquals(TimeshiftMove.None, skipMove(AppTimeshiftState(), 30_000))

        val behind = LiveTimeline(LocalTime.of(20, 0), 3_600, 0, 1_200, 1_800)
        assertEquals(TimeshiftMove.GoLive, dragMove(behind, 1_800))
        assertEquals(TimeshiftMove.None, dragMove(behind, 1_200))
        assertEquals(TimeshiftMove.By(-600_000), dragMove(behind, 600))
        // In an earlier program the bar's end is that program's end, not live.
        val earlier = behind.copy(position = 3_300, live = 3_600, behind = 2_100, liveEdge = false)
        assertEquals(TimeshiftMove.By(300_000), dragMove(earlier, 3_600))
    }

    @Test
    fun bufferStartKeepsItsSnapshotForTheSegment() {
        val fixture = TimeshiftTestFixture(120.minutes)
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        val first = fixture.state.value.toAppPresentation(fixture.playbackPosition(80.minutes))
        val held = heldHistoryStart(null, first)
        assertEquals(first.timeline, held)
        fixture.updateHistory(51.minutes, 91.minutes, estimatedLiveEdgeTime = live + 1.minutes)
        val later = fixture.state.value.toAppPresentation(fixture.playbackPosition(80.minutes))
        assertSame(held, heldHistoryStart(held, later))
        fixture.restartSegment()
        fixture.updateHistory(0.minutes, 1.minutes, estimatedLiveEdgeTime = live + 2.minutes)
        val restarted = fixture.state.value.toAppPresentation(fixture.playbackPosition(1.minutes))
        assertEquals(restarted.timeline, heldHistoryStart(held, restarted))
        assertEquals(null, heldHistoryStart(held, AppTimeshiftState()))
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
        fun face(
            id: at.bernhardberger.tvheadend.sdk.core.ChannelId,
            known: Boolean = true,
            pause: LivePauseAvailability = LivePauseAvailability.READY,
            status: LivePlayerStatus = LivePlayerStatus.Playing,
            window: Boolean = false,
            filtered: Boolean = false,
            degraded: Boolean = false,
            loaded: Boolean = true,
        ) = livePlayerState(
            id,
            ChannelScopeState(
                ChannelBrowsingScope(
                    allChannels = if (known) channels else emptyList(),
                    visibleChannels = if (filtered) listOf(guide.first) else if (known) channels else emptyList(),
                    tags = listOf(guide.news), allChannelsVisible = true, activeTagId = guide.news.id.takeIf { filtered },
                ),
                channelCatalogCurrent = known,
                settingsLoaded = loaded,
            ),
            observation, ConnectionUiState.Ready, degraded, status, mutableStateOf(null), pause, guide.now,
            window, texts = ChannelsTexts(guide.texts.clock, guide.texts.timeRange, guide.texts.duration, { null }, { null }),
        )

        val playing = face(shown)
        assertEquals("Channel One", playing.channel.name)
        assertEquals(guide.programs[0], playing.channel.now?.title)
        assertEquals("30m", playing.channels.displayedList?.rows?.first()?.now?.timeLeft)
        val sheets = setOf(PlayerControl.Channels, PlayerControl.Info)
        val pausable = sheets + PlayerControl.Timeshift
        assertEquals(pausable, playing.offered)
        assertEquals(pausable, face(shown, pause = LivePauseAvailability.STARTING).offered)
        assertEquals(sheets, face(shown, pause = LivePauseAvailability.UNAVAILABLE).offered)
        assertEquals(sheets, face(shown, pause = LivePauseAvailability.OFF).offered)
        assertEquals(sheets, face(shown, status = LivePlayerStatus.Tuning).offered)
        assertEquals(sheets, face(shown, status = LivePlayerStatus.Reconnecting(null)).offered)
        // The picture-in-picture button only where the window can be used.
        assertEquals(
            pausable + PlayerControl.PictureInPicture, face(shown, window = true).offered,
        )
        // The list below the picture marks the shown channel as the one playing.
        assertEquals(listOf(true, false), playing.channels.displayedList?.rows?.map { it.playback != null })
        val saved = face(shown, filtered = true, degraded = true).channels as ChannelsState.Offline
        assertEquals(guide.news.id.value.toString(), saved.list.selectedTagId)
        assertEquals(listOf(shown.value.toString()), saved.list.rows.map { it.id })
        assertSame(ChannelsState.Loading, face(shown, loaded = false).channels)
        // A channel the scope doesn't know yet has no name.
        assertEquals("", face(shown, known = false).channel.name)
        assertTrue(PlayerControl.Info !in face(shown, known = false).offered)
    }

    @Test
    fun infoUsesTheSelectedChannelsGuideAndFollowsTheProgramBehindLive() {
        val watched = guide.snapshot.events.first()
        val clock = GuideClock((guide.now + 45.minutes).epochSeconds, ZoneOffset.UTC)
        val channels = listOf(guide.first, guide.second)
        val observation = guide.connected()
        val programs = ShownPrograms(watched, guide.snapshot.events[1])
        val schedule = requireNotNull(playerSchedule(shown, channels, observation, programs, clock, guide.texts))
        assertEquals(guide.programs, schedule.programs.map { it.title })
        assertEquals(watched.id.value.toString(), schedule.watchingId)
        assertTrue(schedule.programs.first().recording)
        assertTrue(schedule.programs.first { it.title == guide.programs[2] }.scheduled)
        val details = requireNotNull(schedule.details(requireNotNull(schedule.watchingId)))
        assertEquals(watched.description, details.synopsis)
        assertTrue(details.watching)
        val otherSchedule = requireNotNull(playerSchedule(other, channels, observation, programs, clock, guide.texts))
        assertTrue(otherSchedule.programs.isEmpty())
        assertEquals(null, otherSchedule.watchingId)
    }

    @Test
    fun backAndLeavingUseTheWindowWhenThereIsOne() {
        data class Case(val trigger: LeaveTrigger, val available: Boolean, val inWindow: Boolean, val rotating: Boolean)
        val cases = mapOf(
            Case(LeaveTrigger.Back, available = true, inWindow = false, rotating = false) to Leave.EnterWindow,
            // Paused or playing alike: the pause state is no input.
            Case(LeaveTrigger.Back, available = false, inWindow = false, rotating = false) to Leave.Stop,
            Case(LeaveTrigger.Back, available = true, inWindow = true, rotating = false) to Leave.Stay,
            Case(LeaveTrigger.UserLeave, available = true, inWindow = false, rotating = false) to Leave.EnterWindow,
            // Without the window the activity is hidden next, and that stops.
            Case(LeaveTrigger.UserLeave, available = false, inWindow = false, rotating = false) to Leave.Stay,
            Case(LeaveTrigger.UserLeave, available = true, inWindow = true, rotating = false) to Leave.Stay,
            // Hidden outside a rotation: the window closed, or no window.
            Case(LeaveTrigger.Hidden, available = true, inWindow = true, rotating = false) to Leave.Stop,
            Case(LeaveTrigger.Hidden, available = false, inWindow = false, rotating = false) to Leave.Stop,
            Case(LeaveTrigger.Hidden, available = true, inWindow = false, rotating = true) to Leave.Stay,
        )
        cases.forEach { (case, expected) ->
            assertEquals(case.toString(), expected, leavePlayer(case.trigger, case.available, case.inWindow, case.rotating))
        }
        // Watch on the playing channel brings its player back; another channel, or none playing, tunes.
        assertEquals(false, watchTunes(ChannelId(1), ChannelId(1)))
        assertEquals(true, watchTunes(ChannelId(2), ChannelId(1)))
        assertEquals(true, watchTunes(ChannelId(1), null))
    }

    @Test
    fun windowFollowsThePictureInsideAndroidsAspectLimits() {
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) {
                _, method, _ ->
            when (method.name) {
                "getVideoSize" -> VideoSize(1_920, 1_080)
                "getVideoFormat" -> null
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
        assertEquals(4f / 3, pictureAspect(player, AspectRatioMode.FORCE_4_3))
        assertEquals("Auto restores the source aspect", 16f / 9, pictureAspect(player, AspectRatioMode.FIT))
        assertEquals(17_778 to 10_000, windowAspect(pictureAspect(player)))
        assertEquals(17_778 to 10_000, windowAspect(16f / 9))
        assertEquals(239 to 100, windowAspect(3f))
        assertEquals(100 to 239, windowAspect(0.2f))
        listOf(null, 0f, -1f, Float.NaN).forEach { assertEquals(null, windowAspect(it)) }
        // The window's channel steps wrap round the list, as the TV's do.
        val list = listOf(ChannelId(1), ChannelId(2), ChannelId(3))
        assertEquals(ChannelId(2), adjacentChannel(list, ChannelId(1), 1))
        assertEquals(ChannelId(3), adjacentChannel(list, ChannelId(1), -1))
        assertEquals(ChannelId(1), adjacentChannel(list, ChannelId(3), 1))
        assertEquals(ChannelId(1), adjacentChannel(list, ChannelId(9), 1))
        assertEquals(null, adjacentChannel(emptyList(), ChannelId(1), 1))
    }

    @Test
    fun controlsLeaveOnlyWhileThePictureMovesFreely() {
        data class Case(
            val name: String,
            val status: LivePlayerStatus = LivePlayerStatus.Playing,
            val shown: Boolean = true,
            val paused: Boolean = false,
            val progressing: Boolean = true,
            val dragging: Boolean = false,
            val fingerDown: Boolean = false,
            val panelOpen: Boolean = false,
            val inWindow: Boolean = false,
            val leaves: Boolean = false,
        )
        listOf(
            Case("playing", leaves = true),
            Case("already hidden", shown = false),
            Case("paused", paused = true),
            Case("buffering", progressing = false),
            Case("tuning", status = LivePlayerStatus.Tuning),
            Case("reconnecting", status = LivePlayerStatus.Reconnecting(null)),
            Case("pause ended", status = LivePlayerStatus.PauseEnded),
            Case("unavailable", status = LivePlayerStatus.Unavailable()),
            Case("drag on the bar", dragging = true),
            Case("finger resting on the face", fingerDown = true),
            Case("menu or sheet open", panelOpen = true),
            Case("picture-in-picture window", inWindow = true),
        ).forEach {
            val after = controlsHideAfter(
                it.shown, it.status, it.paused, it.progressing, it.dragging, it.fingerDown, it.panelOpen, it.inWindow,
                5_000L,
            )
            assertEquals(it.name, if (it.leaves) 5_000L else null, after)
        }
        // The accessibility timeout stands; touch exploration keeps them for good.
        assertEquals(12_000L, controlsTimeout(12_000, touchExploration = false))
        assertEquals(null, controlsTimeout(12_000, touchExploration = true))
        assertEquals(null, controlsHideAfter(true, LivePlayerStatus.Playing, false, true, false, false, false, false, null))
    }

    @Test
    fun brightnessSliderShowsTheSystemLevelOnTheSystemsCurve() {
        // A dim indoor backlight of about a tenth sits well up the slider, as on the system's own.
        assertTrue(brightnessSlider(0.1f) > 0.4f)
        assertEquals(0f, brightnessSlider(0f), 0.001f)
        assertEquals(1f, brightnessSlider(1f), 0.01f)
        listOf(0.01f, 0.05f, 0.1f, 0.4f, 0.8f, 1f).forEach {
            assertEquals(it, brightnessLevel(brightnessSlider(it)), 0.001f)
        }
    }

    // Program A 19:00-20:00 has aired, B 20:00-21:00 is on; live is 20:30. Content minutes map to the
    // clock through the history's end, which is live (or [edge]).
    private val live = Instant.parse("2026-09-07T20:30:00Z")
    private val programs = EpgSnapshot.create(
        events = listOf(program(2, "2026-09-07T20:00:00Z", "2026-09-07T21:00:00Z")),
        historicalEvents = listOf(program(1, "2026-09-07T19:00:00Z", "2026-09-07T20:00:00Z")),
    )
    private val schedule = SessionObservation.create(epgState = EpgRepositoryState.Current(programs))

    @Test
    fun menusListTheTracksAndOptionsAsTheTvDoes() {
        val lines = listOf(
            HumanTrackLabel("English • Stereo", "English", null, "Stereo • AAC"),
            HumanTrackLabel("German • Stereo • Audio description", "Audio description", "German", "Stereo • AAC"),
        )
        fun tracks(automatic: Boolean = true, default: Boolean = false, off: Boolean = false, picked: Int? = null) =
            playerTracks(lines, 0, lines, picked, TrackSettings(automatic, default, off), "English")
        assertEquals(lines, tracks().audio)
        data class Case(val name: String, val tracks: PlayerTracks, val audio: TrackPick?, val subtitles: TrackPick?)
        listOf(
            Case("Automatic marks only itself", tracks(), TrackPick.Default, null),
            Case("a picked track marks itself", tracks(automatic = false, picked = 1), TrackPick.Track(0), TrackPick.Track(1)),
            Case("Default subtitles over a pick", tracks(default = true, picked = 1), TrackPick.Default, TrackPick.Default),
            Case("subtitles off", tracks(off = true), TrackPick.Default, TrackPick.Off),
        ).forEach {
            assertEquals(it.name, it.audio, it.tracks.audioPick)
            assertEquals(it.name, it.subtitles, it.tracks.subtitlePick)
            assertEquals(it.name, 0, it.tracks.playingAudio)
        }

        // Audio only with another track to choose, Subtitles with one; neither for a picture not yet the shown one.
        assertEquals(setOf(PlayerControl.Settings), optionControls(1, 0, targetCurrent = true))
        assertEquals(
            setOf(PlayerControl.Settings, PlayerControl.Audio, PlayerControl.Subtitles),
            optionControls(2, 1, targetCurrent = true),
        )
        assertEquals(setOf(PlayerControl.Settings), optionControls(2, 1, targetCurrent = false))

        val direct = SdkStreamProfile(StreamProfileId("1".repeat(32)), "htsp", "")
        val small = SdkStreamProfile(StreamProfileId("2".repeat(32)), "h264-720p", "")
        val listed = listOf(direct, small)
        val live = AppPlaybackTarget.Live(ChannelId(1))
        assertEquals(
            PlaybackOptions(
                DisplayMode.Fill4By3, listOf(StreamProfile("htsp", direct = true), StreamProfile("h264-720p")),
                defaultProfile = 1, profilePick = 0, stats = null,
            ),
            playbackOptions(live, listed, small.id, direct.id.value, AspectRatioMode.FORCE_4_3),
        )
        // A setting the server doesn't list is the server's default; no list leaves out the profile row.
        assertEquals(null, playbackOptions(live, listed, null, null, AspectRatioMode.FIT).defaultProfile)
        assertEquals(
            PlaybackOptions(DisplayMode.Auto, emptyList(), defaultProfile = null, stats = null),
            playbackOptions(live, emptyList(), small.id, null, AspectRatioMode.FIT),
        )
        assertEquals(
            PlaybackOptions(DisplayMode.Fill4By3, defaultProfile = null, stats = null),
            playbackOptions(
                AppPlaybackTarget.Recording(DvrEntryId(1)), listed, small.id, direct.id.value, AspectRatioMode.FORCE_4_3,
            ),
        )
        DisplayMode.entries.forEach { assertEquals(it, it.aspectMode.displayMode) }
    }

    private fun program(id: Long, start: String, stop: String) = EpgEvent.create(
        id = EventId(id), channelId = ChannelId(1), start = Instant.parse(start), stop = Instant.parse(stop),
        title = "Program $id",
    )

    private fun eventAt(time: Instant) = schedule.eventAt(ChannelId(1), time)

    private fun timeshift(
        startMinute: Int,
        endMinute: Int,
        position: Duration,
        readerBehind: Duration? = null,
        edge: Instant = live,
    ): AppTimeshiftState {
        val fixture = TimeshiftTestFixture(120.minutes)
        fixture.updateHistory(
            startMinute.minutes, endMinute.minutes, readerBehindLive = readerBehind, estimatedLiveEdgeTime = edge,
        )
        return fixture.state.value.toAppPresentation(fixture.playbackPosition(position))
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
