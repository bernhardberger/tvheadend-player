package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Skip over an ad break: the prompt over the hidden chrome, and the resting Skip the controls
 * reach from Settings and from the marks row only while the break plays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class RecordingSkipFocusTest {
    @get:Rule val compose = createComposeRule()

    private val ad = RecordingMark(RecordingMarkKind.AD_BREAK, 1_500_000L, 2_100_000L)

    // The prompt.

    @Test fun thePromptTakesFocusWhenItAppearsAndAnnouncesItself() {
        val prompt = prompt()
        prompt.offer(ad)
        assertEquals(listOf("recording-skip-prompt"), focused())
        val node = compose.onNodeWithTag("recording-skip-prompt", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
        assertTrue(compose.onNodeWithTag("recording-skip-prompt").fetchSemanticsNode()
            .config[SemanticsProperties.Text].any { it.text == "Skip ad break" })
        assertTrue(exists("recording-skip-fill"))
    }

    @Test fun okOnThePromptSkipsTheBreak() {
        val prompt = prompt()
        prompt.offer(ad)
        key(Key.DirectionCenter)
        assertEquals(listOf(ad), prompt.skips)
    }

    @Test fun aPressHeldFromBeforeThePromptCannotActivateIt() {
        val prompt = prompt()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        prompt.offer(ad)
        assertEquals(listOf("recording-skip-prompt"), focused())
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        assertEquals(emptyList<RecordingMark>(), prompt.skips)
        key(Key.DirectionCenter)
        assertEquals(listOf(ad), prompt.skips)
    }

    @Test fun thePromptLeavesAfterTenSeconds() {
        val prompt = prompt()
        prompt.offer(ad)
        advance(SKIP_PROMPT_MS - 3_000L)
        assertEquals(emptyList<RecordingMark>(), prompt.timeouts)
        assertTrue(exists("recording-skip-prompt"))
        advance(3_200L)
        assertEquals(listOf(ad), prompt.timeouts)
        assertFalse(exists("recording-skip-prompt"))
    }

    @Test fun anotherLayerHidesThePromptWhileItsTimeRunsOn() {
        val prompt = prompt()
        prompt.shown = false
        prompt.offer(ad)
        assertFalse(exists("recording-skip-prompt"))
        advance(SKIP_PROMPT_MS + 200L)
        assertEquals(listOf(ad), prompt.timeouts)
    }

    @Test fun theOfferStandsBackWhilePausedBufferingOrUnseekable() {
        // The screen reads player.isPlaying, which is false both paused and buffering.
        assertTrue(recordingSkipPromptShown(offered = true, playing = true, canSeek = true, bannerVisible = false))
        assertFalse(recordingSkipPromptShown(offered = true, playing = false, canSeek = true, bannerVisible = false))
        assertFalse(recordingSkipPromptShown(offered = true, playing = true, canSeek = false, bannerVisible = false))
        assertFalse(recordingSkipPromptShown(offered = true, playing = true, canSeek = true, bannerVisible = true))
        assertFalse(recordingSkipPromptShown(offered = false, playing = true, canSeek = true, bannerVisible = false))
    }

    @Test fun anOutstandingOfferHidesWhilePausedOrBufferingAndKeepsItsTimeout() =
        outstandingOfferStandsBack(recordingSkipPromptShown(true, playing = false, canSeek = true, bannerVisible = false))

    @Test fun anOutstandingOfferHidesWhenSeekingIsLostAndKeepsItsTimeout() =
        outstandingOfferStandsBack(recordingSkipPromptShown(true, playing = true, canSeek = false, bannerVisible = false))

    private fun outstandingOfferStandsBack(stopped: Boolean) {
        assertFalse(stopped)
        val prompt = prompt()
        prompt.offer(ad)
        assertEquals(listOf("recording-skip-prompt"), focused())
        // Each settle below runs two seconds of the offer's time.
        compose.runOnIdle { prompt.shown = stopped }
        settle()
        assertFalse(exists("recording-skip-prompt"))
        assertFalse("recording-skip-prompt" in focused())
        key(Key.DirectionCenter)
        assertEquals(emptyList<RecordingMark>(), prompt.skips)
        compose.runOnIdle { prompt.shown = true }
        settle()
        assertEquals(listOf("recording-skip-prompt"), focused())
        assertEquals(emptyList<RecordingMark>(), prompt.timeouts)
        // The offer's ten seconds ran on while it stood back; they don't start again.
        advance(2_200L)
        assertEquals(listOf(ad), prompt.timeouts)
    }

    // The resting Skip in the controls.

    @Test fun duringABreakSkipRestsAndPauseKeepsTheInitialFocus() {
        controls(breakPlaying = true)
        assertEquals(listOf("player-pause"), focused())
        assertTrue(exists("recording-skip"))
    }

    @Test fun upFromSettingsReachesSkipAndOkSkipsOntoPause() {
        val controls = controls(breakPlaying = true)
        toSettings()
        key(Key.DirectionUp)
        assertEquals(listOf("recording-skip"), focused())
        key(Key.DirectionCenter)
        assertEquals(listOf(ad), controls.skips)
        assertFalse(exists("recording-skip"))
        assertEquals(listOf("player-pause"), focused())
    }

    @Test fun fromSkipDownReturnsToSettingsAndTheOtherDirectionsStay() {
        controls(breakPlaying = true)
        toSettings()
        key(Key.DirectionUp)
        for (direction in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp)) {
            key(direction)
            assertEquals(direction.toString(), listOf("recording-skip"), focused())
        }
        key(Key.DirectionDown)
        assertEquals(listOf("player-settings"), focused())
    }

    @Test fun upFromTheOtherActionsDuringABreakGoesToTheSeekbar() {
        controls(breakPlaying = true)
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
    }

    @Test fun upFromSettingsOutsideABreakGoesToTheSeekbar() {
        controls(breakPlaying = false)
        assertFalse(exists("recording-skip"))
        toSettings()
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
    }

    @Test fun upFromTheMarksRowDuringABreakClosesItOntoSkipAndOkReturnsToTheSeekbar() {
        val controls = controls(breakPlaying = true)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        assertEquals(listOf("recording-marker-target"), focused())
        // Skip stays in view while the marks row is open.
        assertTrue(exists("recording-skip"))
        key(Key.DirectionUp)
        assertFalse(exists("recording-marker-target"))
        assertEquals(listOf("recording-skip"), focused())
        key(Key.DirectionCenter)
        assertEquals(listOf(ad), controls.skips)
        assertEquals(listOf("player-seekbar"), focused())
    }

    @Test fun downFromSkipReachedFromTheMarksRowReturnsToTheSeekbar() {
        controls(breakPlaying = true)
        repeat(3) { key(Key.DirectionUp) }
        assertEquals(listOf("recording-skip"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("player-seekbar"), focused())
    }

    @Test fun upFromTheMarksRowOutsideABreakClosesItOntoTheSeekbar() {
        controls(breakPlaying = false)
        repeat(3) { key(Key.DirectionUp) }
        assertFalse(exists("recording-marker-target"))
        assertEquals(listOf("player-seekbar"), focused())
    }

    @Test fun aBreakEndingUnderAFocusedSkipReturnsFocusToWhereSkipWasEntered() {
        val controls = controls(breakPlaying = true)
        toSettings()
        key(Key.DirectionUp)
        assertEquals(listOf("recording-skip"), focused())
        compose.runOnIdle { controls.adBreak = null }
        settle()
        assertFalse(exists("recording-skip"))
        assertEquals(listOf("player-settings"), focused())
    }

    private class PromptState {
        var prompt by mutableStateOf<RecordingMark?>(null)
        var shown by mutableStateOf(true)
        val skips = mutableListOf<RecordingMark>()
        val timeouts = mutableListOf<RecordingMark>()
        lateinit var offer: (RecordingMark) -> Unit
    }

    private fun prompt(): PromptState {
        val state = PromptState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                val root = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().focusRequester(root).focusable()) {
                    RecordingSkipPrompt(
                        prompt = state.prompt,
                        shown = state.shown,
                        onSkip = { state.skips += it },
                        onTimeout = { state.timeouts += it; state.prompt = null },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
                LaunchedEffect(Unit) { root.requestFocus() }
            }
        }
        settle()
        state.offer = { mark ->
            compose.runOnIdle { state.prompt = mark }
            settle()
        }
        return state
    }

    private class ControlsState {
        var adBreak by mutableStateOf<RecordingMark?>(null)
        val skips = mutableListOf<RecordingMark>()
    }

    private fun controls(breakPlaying: Boolean): ControlsState {
        val state = ControlsState().apply { adBreak = ad.takeIf { breakPlaying } }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(
                    positionMs = 1_800_000L,
                    marks = listOf(ad) + chapterMarks(900_000L),
                    adBreak = state.adBreak,
                    onSkipAdBreak = { state.skips += it; state.adBreak = null },
                )
            }
        }
        settle()
        return state
    }

    private fun toSettings() {
        repeat(6) { if (focused() != listOf("player-settings")) key(Key.DirectionRight) }
        assertEquals(listOf("player-settings"), focused())
    }

    private fun focused(): List<String> =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(100L, left)
            compose.mainClock.advanceTimeBy(step)
            left -= step
        }
        compose.waitForIdle()
    }

    private fun settle() {
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }
}
