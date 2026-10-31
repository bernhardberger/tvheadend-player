package at.bernhardberger.tvhplayer.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import org.koin.dsl.module

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeActionsTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000L
    private val channel = Channel.create(ChannelId(1), name = "Test channel")

    @Test fun currentLiveOkUsesExistingLiveEntryPath() {
        val tuned = mutableListOf<ChannelId>()
        val observation = SessionObservation.create(channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))))
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), emptyList(), { null },
            HomePlaying.LiveChannel(channel.id), now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> fail("must not restart") },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun activeRecordingOkOffersChoiceAndWatchLiveTunes() {
        val tuned = mutableListOf<ChannelId>()
        recordingHome(running = true, onWatch = { tuned += it }, onPlay = { fail("must not start recording") })
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("recording-watch-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun continueWatchingOkOffersResumeAndBackRestoresCard() {
        val starts = mutableListOf<RecordingPlaybackStart>()
        recordingHome(running = false, onWatch = { fail("must not tune") }, onPlay = { starts += it })
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    private fun recordingHome(running: Boolean, onWatch: (ChannelId) -> Unit, onPlay: (RecordingPlaybackStart) -> Unit) {
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Test recording",
            state = if (running) DvrEntryState.RECORDING else DvrEntryState.COMPLETED,
            start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(if (running) now + 1800 else now),
            playPosition = 120.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now))))
        val initialObservation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(entry))),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED)
        val session = FakeTvheadendSession(initialObservation)
        val observation = session.observation.value
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        val dependencies = module { single<TvheadendSession> { session }; single { notices } }
        val application = org.koin.dsl.koinApplication { modules(dependencies) }
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), listOf(entry), observation::recordingPlaybackAdmission, null, now)
        compose.setContent { org.koin.compose.KoinIsolatedContext(application) {
            androidx.compose.runtime.DisposableEffect(application) { onDispose { application.close() } }
            TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { id, _ -> onWatch(id) }, onPlayRecording = { _, start -> onPlay(start) },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } } }
    }

    @Test fun currentRecordingOkReturnsToWarmSession() {
        val returns = mutableListOf<DvrEntryId>()
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null },
            HomePlaying.Recording(DvrEntryId(9)), now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("must not tune") }, onPlayRecording = { _, _ -> fail("must not restart") },
                onReturnToRecording = { id, _ -> returns += id; true }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrEntryId(9)), returns) }
    }

    @Test fun upcomingSummaryOpensSchedule() {
        val modes = mutableListOf<DvrLibraryMode>()
        val entry = DvrEntry.create(DvrEntryId(1), title = "Tomorrow", state = DvrEntryState.SCHEDULED,
            start = Instant.fromEpochSeconds(now + 3600), stop = Instant.fromEpochSeconds(now + 7200))
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(entry), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { modes += it })
        } }
        compose.onNodeWithTag("home-upcoming").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrLibraryMode.SCHEDULE), modes) }
    }

    @OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun completedRecordingStartsAndEndCardOpensArchive() {
        val starts = mutableListOf<at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart>()
        val modes = mutableListOf<DvrLibraryMode>()
        val entry = DvrEntry.create(DvrEntryId(1), title = "Yesterday", state = DvrEntryState.COMPLETED,
            start = Instant.fromEpochSeconds(now - 3600), stop = Instant.fromEpochSeconds(now - 1800),
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 3600), stop = Instant.fromEpochSeconds(now - 1800))))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))))
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(entry), observation::recordingPlaybackAdmission, null, now)
        assertNotNull(observation.currentSession)
        assertEquals(RecordingOpenAction.START_OVER, recordingOpenAction(observation.recordingPlaybackAdmission(entry)))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, start -> starts += start },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { modes += it })
        } }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, starts.size) }
        compose.onNodeWithTag("home-shelf-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-all-recordings").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(listOf(at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart.START_OVER), starts)
            assertEquals(listOf(DvrLibraryMode.ARCHIVE), modes)
        }
    }

    @Test fun fakeInputRendersRecentAndOkTunesWithoutFocusTuning() {
        val tuned = mutableListOf<ChannelId>()
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.runOnIdle { assertTrue(tuned.isEmpty()) }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun onNowOkAndGenreOkDispatchTheirCallbacks() {
        val tuned = mutableListOf<ChannelId>()
        val genres = mutableListOf<ProgrammeCategory>()
        val event = EpgEvent.create(EventId(1), channelId = channel.id,
            start = Instant.fromEpochSeconds(now - 600), stop = Instant.fromEpochSeconds(now + 600), title = "Test programme",
            contentGenre = ContentGenre.fromContentType(0x40))
        val input = HomeInput(listOf(channel), mapOf(channel.id to event), listOf(event), emptyList(), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = { genres += it }, onRecordings = {})
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-genre-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned); assertEquals(listOf(ProgrammeCategory.SPORT), genres) }
    }
}
