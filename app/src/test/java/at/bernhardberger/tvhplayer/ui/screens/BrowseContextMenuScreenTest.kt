package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseContextMenuScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var view: View
    private var plays = 0
    private var guideRequest: ChannelId? = null
    private var start: RecordingPlaybackStart? = null
    private var watchedChannel: ChannelId? = null

    @Test fun channelsHoldMenuBackAndWatch() {
        show(recordings = false)
        exercise("channel-row-1", "Watch", "channels")
        assertEquals(1, plays)
    }

    @Test fun recordingsHoldMenuBackAndResume() {
        show(recordings = true)
        exercise("recording-list-entry-1", "Resume", "recordings")
        assertEquals(1, plays)
        assertEquals(RecordingPlaybackStart.RESUME, start)
    }

    @Test fun recordingOkShowsResumeAndBackRestoresRow() {
        show(recordings = true)
        val row = compose.onNodeWithTag("recording-list-entry-1").requestFocus()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        assertEquals(0, plays)
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        capture("resume", "u15-dialogs")
        compose.runOnIdle { back.onBackPressed() }
        row.assertIsFocused()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        assertEquals(RecordingPlaybackStart.START_OVER, start)
    }

    @Test fun channelRecordDialogStartsOnSafeChoiceAndReturnsToMenu() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Don't record").assertIsFocused()
        capture("record", "u15-dialogs")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Record").assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun removedRecordingClosesResumeWithoutChangingTheNextContextMenu() {
        val session = show(recordings = true)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
        compose.runOnIdle {
            val previous = session.observation.value
            session.publish(SessionObservation.create(
                sessionState = previous.sessionState,
                channelState = previous.channelState,
                epgState = previous.epgState,
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(
                    previous.dvrSnapshotForDisplay!!.entries.filter { it.id != DvrEntryId(1) })),
            ))
        }
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-list-entry-2").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Details").assertExists()
        assertEquals(0, plays)
    }

    @Test fun scheduledConfirmationBackRestoresCancelAction() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-cancel").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Keep recording").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithText("Keep recording").assertIsFocused()
        capture("cancel-recording", "u15-dialogs")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-cancel").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("recording-list-entry-1").assertIsFocused()
    }

    @Test fun channelsShowGuideUsesTheOriginatingChannel() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Show in Guide").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(ChannelId(1), guideRequest)
        assertEquals(0, plays)
    }

    @Test fun contextPublicationPreservesSurvivingActionFocus() {
        val session = show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Show in Guide").requestFocus().assertIsFocused()
        compose.runOnIdle {
            val previous = session.observation.value
            session.publish(SessionObservation.create(previous.sessionState, previous.channelState, previous.epgState,
                DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(DvrEntryId(9),
                    eventId = EventId(1), channelId = ChannelId(1), state = DvrEntryState.SCHEDULED))))))
        }
        compose.onNodeWithText("Cancel recording").assertExists()
        compose.onNodeWithText("Show in Guide").assertIsFocused()
    }

    @Test fun missingMenuChannelClosesInsteadOfTrappingChannels() {
        val session = show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertExists()
        compose.runOnIdle {
            val previous = session.observation.value
            session.publish(SessionObservation.create(previous.sessionState,
                ChannelRepositoryState.Current(ChannelCatalog.create()), previous.epgState, previous.dvrState))
        }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
    }

    @Test fun absentChannelAtMenuEntryCallsClose() {
        var closes = 0
        val loader = coil3.ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        compose.setContent {
            TVHeadendPlayerTheme {
                ChannelContextMenu(ChannelId(99), SessionObservation.create(), 0, loader,
                    onPlay = { _, _ -> }, onShowGuide = {}, onClose = { closes++ })
            }
        }
        compose.runOnIdle { assertEquals(1, closes) }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        loader.shutdown()
    }

    @Test fun confirmationAfterPoppingDetailsRestoresItsActionNotTheCard() {
        show(recordings = false)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-card-2").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Don't record").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-record").assertIsFocused()
    }

    @Test fun recordingsDetailsBackRestoresTheOriginatingRow() {
        show(recordings = true)
        val row = compose.onNodeWithTag("recording-list-entry-1")
        row.requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-resume").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithTag("details-resume").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        row.assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun channelsDetailsStackBackAndOpeningReleaseRestoreExactOrigin() {
        show(recordings = false)
        val row = compose.onNodeWithTag("channel-row-1").requestFocus()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        assertEquals(0, plays)
        compose.onNodeWithTag("details-watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithTag("details-card-2").assertDoesNotExist()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        row.assertIsFocused()
        assertEquals(0, plays)
    }

    @Test fun channelsDetailsCanPlayItsCompletedRecording() {
        val session = show(recordings = false, channelRecording = true)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Program details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            val previous = session.observation.value
            val event = requireNotNull(previous.event(EventId(1)))
            session.publish(SessionObservation.create(previous.sessionState, previous.channelState,
                EpgRepositoryState.Current(EpgSnapshot.create(listOf(EpgEvent.create(event.id, event.channelId,
                    event.start, Instant.fromEpochMilliseconds(System.currentTimeMillis()) - 60.seconds,
                    title = event.title)))), previous.dvrState))
        }
        compose.onNodeWithTag("details-play").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, plays)
        assertEquals(RecordingPlaybackStart.START_OVER, start)
    }

    @Test fun scheduledDetailsWatchChannelUsesLivePlayback() {
        show(recordings = true, recordingState = DvrEntryState.SCHEDULED)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(ChannelId(1), watchedChannel)
        assertEquals(0, plays)
    }

    @Test fun deniedDvrPermissionKeepsRecordingPlaybackButRemovesDelete() {
        show(recordings = true, dvrWrite = CapabilityAccess.DENIED)
        compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Resume").assertIsFocused()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test fun deniedDvrPermissionRemovesChannelRecord() {
        show(recordings = false, dvrWrite = CapabilityAccess.DENIED)
        compose.onNodeWithTag("channel-row-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Watch").assertIsFocused()
        compose.onNodeWithText("Record").assertDoesNotExist()
    }

    private fun exercise(rowTag: String, first: String, capture: String) {
        val row = compose.onNodeWithTag(rowTag, useUnmergedTree = true).requestFocus()
        row.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN))
        row.performKeyPress(activation(android.view.KeyEvent.ACTION_DOWN, 1))
        compose.onNodeWithText(first).assertIsFocused()
        compose.onRoot().performKeyPress(activation(android.view.KeyEvent.ACTION_UP))
        assertEquals("held release must not activate playback", 0, plays)
        compose.onNodeWithText(first).assertIsFocused()
        listOf(Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight).forEach {
            compose.onRoot().performKeyInput { pressKey(it) }
            compose.onNodeWithText(first).assertIsFocused()
        }
        capture(capture)
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        row.assertIsFocused()
        row.performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText(first).assertIsFocused()
        assertEquals(0, plays)
        compose.onNodeWithText(first).performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun activation(action: Int, repeat: Int = 0) = KeyEvent(android.view.KeyEvent(
        0, 0, action, android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat,
    ))

    private fun capture(name: String, output: String = "u15-context") {
        compose.mainClock.advanceTimeBy(500)
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/$output").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun show(recordings: Boolean, dvrWrite: CapabilityAccess = CapabilityAccess.ALLOWED,
        recordingState: DvrEntryState = DvrEntryState.COMPLETED, channelRecording: Boolean = false): FakeTvheadendSession {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
        val otherChannel = Channel.create(ChannelId(2), name = "Ridge Earth SD", number = 100, icon = ArtworkId(1))
        val event = EpgEvent.create(EventId(1), channel.id, now - 1800.seconds, now + 1800.seconds,
            title = "Ridge Light", image = ArtworkId(2))
        val next = EpgEvent.create(EventId(2), channel.id, event.stop, event.stop + 3600.seconds,
            title = "Northern Journey", image = ArtworkId(2))
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, eventId = event.id,
            title = if (recordingState == DvrEntryState.COMPLETED) "Ridge Light: The remarkable journey along the mountain ridge in winter" else "Ridge Light",
            folder = emptyList(),
            channelName = channel.name, state = recordingState, playPosition = 3720.seconds,
            start = if (recordingState == DvrEntryState.SCHEDULED) now + 3600.seconds else now - 7200.seconds,
            stop = if (recordingState == DvrEntryState.SCHEDULED) now + 9000.seconds else now - 1800.seconds, image = ArtworkId(2))
        val otherEntry = DvrEntry.create(DvrEntryId(2), channelId = channel.id, title = "Ridge Light: Episode 2",
            folder = emptyList(), channelName = channel.name, state = DvrEntryState.COMPLETED,
            start = now - 3500.seconds, stop = now - 100.seconds, image = ArtworkId(2))
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = dvrWrite)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(otherChannel, channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(event, next))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(if (recordings) listOf(otherEntry, entry)
                else if (channelRecording) listOf(entry) else emptyList())),
        ))
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, session.observation.value.currentSession?.generationIdentity) }
        val dependencies = module { single<TvheadendSession> { session }; single { notices } }
        val application = org.koin.dsl.koinApplication { modules(dependencies) }
        val loader = FixtureArt.imageLoader(context, mapOf(ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light")))
        val state = RecordingsScreenState().apply {
            if (recordingState == DvrEntryState.SCHEDULED) mode.value = at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE
        }
        compose.setContent {
            org.koin.compose.KoinIsolatedContext(application) {
                androidx.compose.runtime.DisposableEffect(application) {
                    onDispose { application.close() }
                }
                TVHeadendPlayerTheme {
                    back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    view = LocalView.current
                    val observation = session.observation.collectAsState().value
                    Box(Modifier.fillMaxSize().background(androidx.tv.material3.MaterialTheme.colorScheme.background)) {
                        if (recordings) RecordingsScreenContent(
                            observation = observation, state = state, imageLoader = loader,
                            dvrMutationActions = DvrMutationActions(session.dvrRepository), notices = notices,
                            onPlayRecording = { _, intent -> plays++; start = intent },
                            onWatchChannel = { selection, _ -> watchedChannel = selection.channelId },
                        ) else ChannelsScreenContent(
                            channelScopeState = resolveChannelScopeState(observation.channelState, null),
                            observation = observation, tagNotice = false, selectedId = { channel.id },
                            imageLoader = loader, playingChannelId = null, connectionUiState = ConnectionUiState.Ready,
                            onSelectChannel = {}, onSelectTag = {}, onDismissTagNotice = {},
                            onRetryConnection = {}, onOpenConnectionSettings = {}, onPlay = { _, _ -> plays++ },
                            onShowGuide = { guideRequest = it },
                            onPlayRecording = { plays++; start = RecordingPlaybackStart.START_OVER },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(if (recordings) "recording-list-entry-1" else "channel-row-1").fetchSemanticsNodes().isNotEmpty()
        }
        return session
    }
}
