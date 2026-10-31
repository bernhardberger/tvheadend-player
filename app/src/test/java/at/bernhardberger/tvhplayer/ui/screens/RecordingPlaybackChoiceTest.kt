package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(TvheadendTestingApi::class, ExperimentalTvheadendApi::class)
class RecordingPlaybackChoiceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeChoiceStartsAtResumeAndNavigatesInOrder() {
        var watched = 0
        val starts = mutableListOf<RecordingPlaybackStart>()
        show(754L, watch = { watched++ }, play = { starts += it })
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Watch channel").assertExists()
        compose.onNodeWithText("Watch from beginning").assertExists()
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.START_OVER), starts); assertEquals(0, watched) }
    }

    @Test fun activeChoiceWithoutResumeSkipsResumeAndBackCloses() {
        val visible = mutableStateOf(true)
        var watched = 0
        show(null, watch = { watched++ }, visible = visible, close = { visible.value = false })
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("recording-watch-live").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, watched) }
    }

    @Test fun ordinaryChoiceStillFocusesResumeAndDispatchesResume() {
        val starts = mutableListOf<RecordingPlaybackStart>()
        show(754L, play = { starts += it })
        compose.onNodeWithTag("recording-watch-live").assertDoesNotExist()
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    private fun show(position: Long?, watch: (() -> Unit)? = null,
        play: (RecordingPlaybackStart) -> Unit = {},
        visible: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true), close: () -> Unit = {}) {
        val now = 1_800_000_000L
        val channel = Channel.create(ChannelId(1), name = "Test channel")
        val entry = DvrEntry.create(DvrEntryId(1), title = "Test recording", state = DvrEntryState.RECORDING,
            channelId = channel.id, start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now + 1800),
            playPosition = position?.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now))))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = listOf(channel))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED)
        compose.setContent {
            TVHeadendPlayerTheme {
                if (visible.value) RecordingPlaybackChoice(entry, observation.recordingPlaybackAdmission(entry),
                    observation, channel, now, play, close, watch)
            }
        }
    }
}
