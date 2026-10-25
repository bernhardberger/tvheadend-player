package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerProgrammeDetailsTest {
    @get:Rule val compose = createComposeRule()
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth", number = 101)
    private val watched = EpgEvent.create(EventId(1), channel.id,
        Instant.fromEpochSeconds(1_000), Instant.fromEpochSeconds(3_000), title = "Watched program")

    @Test fun liveDetailsOpensWatchedProgrammeAndBackRestoresInfo() {
        show(recording = false)
        open()
        compose.onNodeWithText("Watched program").assertExists()
        compose.onNodeWithTag("details-title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag("details-watch").assertDoesNotExist()
        compose.onNodeWithTag("details-show_guide").assertExists()
        back()
    }

    @Test fun recordingDetailsIsReadOnlyAndBackRestoresInfo() {
        show(recording = true)
        open()
        compose.onNodeWithText("Recorded program").assertExists()
        compose.onNodeWithTag("details-title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        for (action in listOf("play", "delete", "record", "stop", "cancel", "show_guide")) {
            compose.onNodeWithTag("details-$action").assertDoesNotExist()
        }
        back()
    }

    @Test fun continueWatchingClosesWithoutAPlaybackCommand() {
        show(recording = false)
        open()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("player-info").assertIsFocused()
        compose.onNodeWithTag("details-continue_watching").assertDoesNotExist()
    }

    @Test fun recordingContinueWatchingRestoresInfoWithoutAPlaybackCommand() {
        show(recording = true)
        open()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        compose.onNodeWithTag("player-info").assertIsFocused()
        compose.onNodeWithTag("details-continue_watching").assertDoesNotExist()
    }

    @Test fun showInGuideRequestsTheWatchedProgrammeWithoutAPlaybackCommand() {
        var destination: Pair<ChannelId, EventId?>? = null
        show(recording = false, onShowGuide = { channelId, programme ->
            destination = channelId to programme?.id
        })
        open()
        compose.onNodeWithTag("details-show_guide").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        assertEquals(channel.id to watched.id, destination)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
    }

    private fun open() {
        compose.onNodeWithTag("player-info").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        // The opening OK did not activate the newly focused Continue watching action.
        compose.onNodeWithTag("details-continue_watching").assertIsFocused()
    }

    private fun back() {
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        settle()
        compose.onNodeWithTag("details-continue_watching").assertDoesNotExist()
        compose.onNodeWithTag("player-info").assertIsFocused()
    }

    private fun show(recording: Boolean, onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> }) {
        val observation = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.DENIED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(watched))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).observation.value
        val mutations = DvrMutationActions(
            scheduleEntry = { _, _ -> error("Unexpected DVR mutation") },
            stopEntry = { _, _ -> error("Unexpected DVR mutation") },
            cancelEntry = { _, _ -> error("Unexpected DVR mutation") },
            deleteEntry = { _, _ -> error("Unexpected DVR mutation") },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected DVR mutation") })
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, null) }
        val settings = UiSettingsStore(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            var opened by remember { mutableStateOf(false) }
            var restore by remember { mutableStateOf<String?>("player-info") }
            val scope = rememberCoroutineScope()
            val close = { opened = false; restore = "player-info" }
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("20:15", PlayerInfoBarData("101", "Ridge Earth", "Chrome title", "")),
                    timeline = if (recording) PlayerChromeTimeline.Recording(0L, 3_000L)
                        else PlayerChromeTimeline.Live(AppTimeshiftState(), nowSec = 2_000),
                    actions = PlayerChromeActions(active = !opened, paused = false, restoreFocus = restore),
                    onTogglePause = { error("Unexpected pause") }, onSeek = { error("Unexpected seek") },
                    onStop = { error("Unexpected stop") }, onInfo = { opened = true }, onOptions = {},
                    onInteraction = {}, onFocusRestored = { restore = null },
                )
                if (opened) {
                    if (recording) RecordingPlayerDetails(DvrEntry.create(DvrEntryId(1),
                        title = "Recorded program", description = "Recording synopsis"), channel, 2_000, close)
                    else LivePlayerProgrammeDetails(watched, channel, observation, { observation }, 2_000,
                        mutations, notices, scope, settings,
                        onPlay = { _, _ -> error("Unexpected playback command") },
                        onShowGuide = onShowGuide, onClose = close)
                }
            }
        }
        settle()
    }

    private fun settle() { compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle() }
}
