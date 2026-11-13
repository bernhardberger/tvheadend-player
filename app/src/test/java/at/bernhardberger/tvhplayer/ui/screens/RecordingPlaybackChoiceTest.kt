package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
            .performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight); pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("recording-watch-live").assertTextEquals("Watch live")
        compose.onNodeWithText("Watch from beginning").assertExists()
        assertTopToBottom("recording-resume", "recording-start-over", "recording-watch-live")
        val card = compose.onNodeWithTag("centred-dialog-card").getUnclippedBoundsInRoot()
        val buttons = listOf("recording-resume", "recording-start-over", "recording-watch-live")
            .map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        buttons.forEach { bounds ->
            assertEquals("Every choice spans the same column", buttons.first().left, bounds.left)
            assertEquals("Every choice spans the same column", buttons.first().right, bounds.right)
            assertTrue("Choices stay inside the card", bounds.left > card.left && bounds.right < card.right)
        }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.START_OVER), starts); assertEquals(0, watched) }
    }

    @Test fun activeChoiceWithoutResumeSkipsResumeAndBackCloses() {
        val visible = mutableStateOf(true)
        var watched = 0
        show(null, watch = { watched++ }, visible = visible, close = { visible.value = false })
        compose.onNodeWithText("Play recording").assertExists()
        compose.onNodeWithText("Watch from beginning").assertDoesNotExist()
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        assertTopToBottom("recording-start-over", "recording-watch-live")
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
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

    @Test fun deferredChoiceExplainsWhyAndOnlyOffersWatchLive() {
        var watched = 0
        val starts = mutableListOf<RecordingPlaybackStart>()
        show(754L, watch = { watched++ }, play = { starts += it }, serverPath = "test.mp4")
        val reason = ApplicationProvider.getApplicationContext<Application>().getString(R.string.error_305_reason)
        compose.onNodeWithText(reason).assertIsDisplayed()
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-start-over").assertDoesNotExist()
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight); pressKey(Key.DirectionUp); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, watched); assertTrue(starts.isEmpty()) }
    }

    @Test fun runningChoiceThatCannotPlayOnlyOffersWatchLiveWithoutTheFinishedReason() {
        show(754L, watch = {}, progress = RecordingProgressCapability.UNSUPPORTED)
        val reason = ApplicationProvider.getApplicationContext<Application>().getString(R.string.error_305_reason)
        compose.onNodeWithText(reason).assertDoesNotExist()
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-start-over").assertDoesNotExist()
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
    }

    @Test fun eachPlayableChoiceStatesItsOwnEndAndFocusNeverResizesTheCard() {
        // A minute before the stop, with five minutes of pre-padding already on disk: 64 of 65 minutes.
        show(754L, watch = {}, left = 60, startExtra = 5.minutes)
        val before = compose.onNodeWithTag("centred-dialog-card").getUnclippedBoundsInRoot()
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun endsAt(sec: Long?) = SemanticsMatcher("ends at $sec") {
            it.config.getOrElseNullable(SemanticsProperties.StateDescription) { null } ==
                sec?.let { end -> app.getString(R.string.recording_ends_at, formatClock(end)) }
        }
        // A growing recording plays on to its padded stop, not to what is on disk.
        compose.onNodeWithTag("recording-resume").assert(endsAt(NOW + 3900 - 754))
        compose.onNodeWithTag("recording-start-over").assert(endsAt(NOW + 3900)).assertTextEquals("Watch from beginning")
        compose.onNodeWithTag("recording-watch-live").assert(endsAt(null))
        // The status line's total is that same length, so the recorded part never passes it.
        compose.onNodeWithText(app.getString(R.string.recording_recorded_of,
            formatRecordedDuration(3840, app.resources), formatRecordedDuration(3900, app.resources)),
            substring = true).assertExists()
        compose.onNodeWithTag("recording-resume").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-start-over").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
        assertEquals(before, compose.onNodeWithTag("centred-dialog-card").getUnclippedBoundsInRoot())
    }

    private fun assertTopToBottom(vararg tags: String) {
        tags.toList().zipWithNext().forEach { (upper, lower) ->
            assertTrue(compose.onNodeWithTag(upper).getUnclippedBoundsInRoot().bottom <
                compose.onNodeWithTag(lower).getUnclippedBoundsInRoot().top)
        }
    }

    private fun show(position: Long?, watch: (() -> Unit)? = null,
        play: (RecordingPlaybackStart) -> Unit = {},
        visible: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true), close: () -> Unit = {},
        serverPath: String = "test.ts", progress: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        left: Long = 1800, startExtra: Duration = Duration.ZERO) {
        val now = NOW
        val channel = Channel.create(ChannelId(1), name = "Test channel")
        val start = Instant.fromEpochSeconds(now - 3600 + left)
        val entry = DvrEntry.create(DvrEntryId(1), title = "Test recording", state = DvrEntryState.RECORDING,
            channelId = channel.id, start = start, stop = Instant.fromEpochSeconds(now + left),
            startExtraMinutes = startExtra, playPosition = position?.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = serverPath,
                start = start - startExtra, stop = Instant.fromEpochSeconds(now))))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = listOf(channel))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
            recordingProgressCapability = progress)
        compose.setContent {
            TVHeadendPlayerTheme {
                if (visible.value) RecordingPlaybackChoice(entry, observation.recordingPlaybackAdmission(entry),
                    observation, channel, now, play, close, watch)
            }
        }
    }

    private companion object { const val NOW = 1_800_000_000L }
}
