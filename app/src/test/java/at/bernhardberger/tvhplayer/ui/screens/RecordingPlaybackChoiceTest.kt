package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.R
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
        compose.onNodeWithTag("recording-watch-live").assertTextEquals("Watch live")
        compose.onNodeWithText("Watch from beginning").assertExists()
        assertLeftToRight("recording-resume", "recording-start-over", "recording-watch-live")
        val content = compose.onNodeWithTag("centred-dialog-content").getUnclippedBoundsInRoot()
        val buttons = listOf("recording-resume", "recording-start-over", "recording-watch-live")
            .map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        buttons.forEach { bounds ->
            assertEquals("All actions share one line", buttons.first().top, bounds.top)
            assertTrue("Actions stay inside the card padding", bounds.left >= content.left && bounds.right <= content.right)
        }
        val card = compose.onNodeWithTag("centred-dialog-card").getUnclippedBoundsInRoot()
        val padding = (content.left - card.left) + (card.right - content.right)
        assertEquals("The card follows the action row, without an empty band",
            (buttons.last().right - buttons.first().left + padding).value,
            (card.right - card.left).value, 1f)
        val liveLabel = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText("Watch live") and hasAnyAncestor(hasTestTag("recording-watch-live")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(liveLabel) }
        assertFalse("The live action has room for its complete label", liveLabel.single().hasVisualOverflow)
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
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
        assertLeftToRight("recording-start-over", "recording-watch-live")
        compose.onNodeWithTag("centred-dialog-card").assertWidthIsEqualTo(528.dp)
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("recording-start-over").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
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
        show(754L, watch = { watched++ }, play = { starts += it }, serverPath = "test.mkv")
        val reason = ApplicationProvider.getApplicationContext<Application>().getString(R.string.error_305_reason)
        compose.onNodeWithText(reason).assertIsDisplayed()
        compose.onNodeWithTag("recording-resume").assertDoesNotExist()
        compose.onNodeWithTag("recording-start-over").assertDoesNotExist()
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
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

    @Test fun endsAtInEyebrowDoesNotResizeDialogWhenWatchingChannel() {
        show(754L, watch = {})
        val before = compose.onNodeWithTag("centred-dialog-card").getUnclippedBoundsInRoot()
        compose.onNodeWithText("Ends at", substring = true).assertExists()
        compose.onNodeWithTag("recording-resume").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-start-over").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
        compose.onNodeWithText("Ends at", substring = true).assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("centred-dialog-card").getUnclippedBoundsInRoot())
    }

    private fun assertLeftToRight(vararg tags: String) {
        tags.toList().zipWithNext().forEach { (left, right) ->
            assertTrue(compose.onNodeWithTag(left).getUnclippedBoundsInRoot().right <
                compose.onNodeWithTag(right).getUnclippedBoundsInRoot().left)
        }
    }

    private fun show(position: Long?, watch: (() -> Unit)? = null,
        play: (RecordingPlaybackStart) -> Unit = {},
        visible: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true), close: () -> Unit = {},
        serverPath: String = "test.ts", progress: RecordingProgressCapability = RecordingProgressCapability.SUPPORTED) {
        val now = 1_800_000_000L
        val channel = Channel.create(ChannelId(1), name = "Test channel")
        val entry = DvrEntry.create(DvrEntryId(1), title = "Test recording", state = DvrEntryState.RECORDING,
            channelId = channel.id, start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now + 1800),
            playPosition = position?.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = serverPath,
                start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now))))
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
}
