package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.compose.runtime.mutableLongStateOf
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.GrowingTimelineDisplayEnd
import at.bernhardberger.tvhplayer.core.RecordingFinishedAction
import at.bernhardberger.tvhplayer.core.RecordingTimelinePresentation
import at.bernhardberger.tvhplayer.core.recordingFinishedAction
import at.bernhardberger.tvhplayer.core.recordingTimelinePresentation
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.touch.recordings.FixtureClock
import at.bernhardberger.tvhplayer.touch.recordings.FixtureRecordingsTexts
import at.bernhardberger.tvhplayer.touch.recordings.RecordingProgress
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(UnstableApi::class)
@kotlin.OptIn(TvheadendTestingApi::class, ExperimentalTvheadendApi::class)
class RecordingPlayerMappingTest {
    private val now = Instant.parse("2026-11-11T21:00:00Z")
    private val server = ServerGuide(now)
    private val target = AppPlaybackTarget.Recording(server.recordingNow.id)
    private val token = RecordingSkipAction(fixtureAd, fixtureAd.endMs)
    private val clock = FixtureClock.copy(now = now.toJavaInstant())
    private val info = recordingPlayerInfo(server.recordingNow, null, clock, FixtureRecordingsTexts)

    @Test
    fun `recording watch starts a new target unless that recording is already playing`() {
        assertEquals(RecordingPlaybackStart.RESUME, recordingStart(false))
        assertEquals(RecordingPlaybackStart.START_OVER, recordingStart(true))
        assertFalse(startsRecording(target.recordingId, target))
        for (other in listOf(null, AppPlaybackTarget.Live(server.first.id), AppPlaybackTarget.Recording(server.film.id))) {
            assertTrue(startsRecording(target.recordingId, other))
        }
    }

    @Test
    fun `runtime exposes only wired recording actions`() {
        val offered = map(AppPlaybackState.Playing, true).offered + PlayerControl.PictureInPicture
        assertEquals(
            setOf(PlayerControl.FullScreen, PlayerControl.Info, PlayerControl.Timeshift, PlayerControl.PictureInPicture),
            recordingRuntimeControls(offered),
        )
        assertEquals(emptySet<PlayerControl>(), recordingRuntimeControls(emptySet()))
    }

    @Test
    fun `growing requires current session and fresh growing admission`() {
        val entry = DvrEntry.create(
            id = target.recordingId, start = now - 30.minutes, stop = now + 30.minutes,
            state = DvrEntryState.RECORDING,
            files = listOf(TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = "fixture.ts")),
        )
        val observation = server.connected(listOf(entry))
        val selection = requireNotNull(currentRecordingPlaybackSelection(observation, entry.id))
        val installed = observation.recordingPlaybackAdmission(entry)
        assertTrue(recordingIsCurrentlyGrowing(observation, selection, installed))
        assertFalse(recordingIsCurrentlyGrowing(observation, selection, null))
        assertFalse(recordingIsCurrentlyGrowing(observation, selection.copy(recordingId = server.film.id), installed))
        assertFalse(recordingIsCurrentlyGrowing(server.connected(listOf(entry)), selection, installed))
    }

    @Test
    fun `runtime face owns metadata status offered controls and exact skip tokens`() {
        for ((playback, playWhenReady) in listOf(
            AppPlaybackState.Playing to true,
            AppPlaybackState.Playing to false,
            AppPlaybackState.Buffering to true,
        )) {
            val state = map(playback, playWhenReady)
            assertEquals(server.recordingNow.title, state.title)
            assertEquals(server.first.name, state.channel.name)
            assertEquals(server.recordingNow.start.toString(), state.recordedOn)
            assertEquals(server.recordingNow.stop.toString(), state.endsAt)
            assertEquals(playback == AppPlaybackState.Buffering, state.buffering)
            assertEquals(!playWhenReady, state.paused)
            assertEquals(LivePlayerStatus.Playing, state.status)
            assertTrue(PlayerControl.Timeshift in state.offered)
            assertTrue(PlayerControl.Info in state.offered)
            assertSame(info, state.info)
            assertFalse(PlayerControl.Audio in state.offered)
            assertFalse(PlayerControl.PictureInPicture in state.offered)
            assertFalse(PlayerControl.Record in state.offered || PlayerControl.Channels in state.offered)
            assertSame(token, state.skipPrompt)
            assertSame(token, state.controlsSkip)
            assertEquals(AdBreakMode.ASK, state.adBreakSetting)
            assertEquals(AdBreakMode.OFF, state.adBreakPick)
        }
    }

    @Test
    fun `info projects a running recording to its padded stop and rejects another recording's details`() {
        val start = now - 30.minutes
        val stop = now + 30.minutes
        val running = DvrEntry.create(
            id = server.recordingNow.id, start = start, stop = stop, stopExtraMinutes = 10.minutes,
            state = DvrEntryState.RECORDING, title = server.recordingNow.title, description = "Match synopsis",
        )
        val playing = recordingPlayerInfo(running, null, clock, FixtureRecordingsTexts)
        assertEquals((stop + 10.minutes).toJavaInstant(), (playing.item.state as RecordingProgress.Recording).until)
        assertEquals(running.description, playing.item.details.synopsis)
        assertFalse(playing.item.hasPicture)
        assertNull(playing.item.remove)
        assertTrue(playing.item.play.isEmpty())

        val completed = recordingPlayerInfo(server.film, null, clock, FixtureRecordingsTexts)
        val other = map(AppPlaybackState.Playing, playWhenReady = true, info = completed)
        assertNull(other.info)
        assertFalse(PlayerControl.Info in other.offered)
    }

    @Test
    fun `shared failure replaces playback controls and rejects stale actions`() {
        val state = map(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER), true,
            RecordingPlaybackAdmission.GrowingDeferred)
        assertEquals(LivePlayerStatus.Unavailable(AppErrorCondition.RECORDING_DEFERRED), state.status)
        assertFalse(PlayerControl.Timeshift in state.offered)
        assertNull(state.skipPrompt)
        assertNull(state.controlsSkip)
    }

    @Test
    fun `the displayed head can lead while every seek stays within verified duration`() {
        val growingEnd = GrowingTimelineDisplayEnd()
        growingEnd.update(60_000L, 0L, true)
        val timeline = recordingTimelinePresentation(64_000L, 60_000L, true, growingEnd.update(60_000L, 8_000L, true))
        val range = (timeline as RecordingTimelinePresentation.Seekable).range
        assertEquals(65_000L, range.displayEndMs)
        assertEquals(60_000L, range.endMs)
        val live = "head"
        listOf(
            Triple(54_999L, true, false), Triple(55_000L, true, true), Triple(60_000L, false, false),
        ).forEach { (position, growing, atHead) ->
            assertEquals(atHead, recordingPositionLabel(position, range.endMs, range.displayEndMs, growing, live) == live)
            assertEquals(atHead, RecordingMove.Seek(position).stepReadout(range, 0L, growing, live).atLive)
        }
        val case = listOf(-1L to 0L, 24_000L to 24_000L, 64_000L to 60_000L)
        case.forEach { (targetMs, expected) ->
            assertEquals(RecordingMove.Seek(expected), recordingDragMove(range, targetMs))
        }
        assertNull(recordingDragMove(null, 10_000))
        val stops = listOf(
            Triple(PlayerAction.BackTen, null, 54_000L),
            Triple(PlayerAction.BackTen, 6_000L, 0L),
            Triple(PlayerAction.ForwardThirty, 1_000L, 31_000L),
            Triple(PlayerAction.ForwardThirty, null, 60_000L),
        )
        stops.forEach { (action, pending, expected) ->
            assertEquals(RecordingMove.Seek(expected), recordingSkipMove(range, action, pending))
        }
        assertNull(recordingSkipMove(null, PlayerAction.BackTen))
    }

    @Test
    fun `marker jump forwards the revision and programme stops shown on that face`() {
        val state = map(AppPlaybackState.Playing, true)
        assertEquals(RecordingMove.Marker(fixtureAd.startMs, 7, state.programMarks), recordingMarkerMove(state, fixtureAd.startMs))
        assertNull(recordingMarkerMove(state, fixtureAd.startMs + 12))
        assertNull(recordingMarkerMove(state.copy(markerRevision = null), fixtureAd.startMs))
    }

    @Test
    fun `finish stops only an active recording and closes only its visible player`() {
        val id = target.recordingId
        listOf(
            Triple(false, id, true) to RecordingFinishedAction.NONE,
            Triple(true, null, true) to RecordingFinishedAction.NONE,
            Triple(true, id, false) to RecordingFinishedAction.STOP,
            Triple(true, id, true) to RecordingFinishedAction.STOP_AND_CLOSE_PLAYER,
        ).forEach { (case, expected) ->
            assertEquals(expected, recordingFinishedAction(case.first, case.second, case.third))
        }
    }

    private fun map(
        playback: AppPlaybackState,
        playWhenReady: Boolean,
        admission: RecordingPlaybackAdmission? =
            TvheadendTestResultFactory.growingRecordingAdmission(null, RecordingProgressCapability.SUPPORTED),
        info: RecordingPlayerInfo? = this.info,
    ) = recordingPlayerState(
        target, server.connected(), playback, admission,
        recordingTimelinePresentation(RECORDING_INSIDE_AD, RECORDING_LENGTH, true),
        mutableLongStateOf(RECORDING_INSIDE_AD), listOf(fixtureAd), 7, token, token, AdBreakMode.ASK, AdBreakMode.OFF,
        playWhenReady, now, Instant::toString, Instant::toString, { "Episode" }, "Unknown channel", "Recording",
        PlayerTracks(), PlaybackOptions(), .5f, allowPictureInPicture = false, info = info,
    )
}
