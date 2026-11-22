package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.testutil.tagExists
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.focusedTags
import at.bernhardberger.tvhplayer.testutil.changeState
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.presentation.R
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertTextEquals
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.playback.RecordingSkipAction
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hidden-controls Skip prompt, and controls-up navigation without a resting Skip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingSkipFocusTest {
    @get:Rule val compose = createComposeRule()

    private val ad = RecordingMark(RecordingMarkKind.AD_BREAK, 1_500_000L, 2_100_000L)

    // The prompt.

    @Test fun thePromptTakesFocusWhenItAppearsAndAnnouncesItself() {
        val prompt = prompt()
        prompt.offer(ad)
        assertEquals(listOf("recording-skip-prompt"), compose.focusedTags())
        val node = compose.onNodeWithTag("recording-skip-prompt", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        assertTrue(compose.onNodeWithTag("recording-skip-prompt").fetchSemanticsNode()
            .config[SemanticsProperties.Text].any { it.text == "Skip ad break" })
        assertTrue(compose.tagExists("recording-skip-countdown"))
        assertFalse(compose.tagExists("recording-skip-fill"))
        assertEquals(null, node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(null, node.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun okOnThePromptSkipsTheBreak() {
        val prompt = prompt()
        prompt.offer(ad)
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals(listOf(ad), prompt.skips)
    }

    @Test fun programStartUsesItsLabelAndOkPassesItsTarget() {
        val start = RecordingMark(RecordingMarkKind.PROGRAM_START, 180_000L)
        val prompt = prompt()
        prompt.offer(start)
        val label = ApplicationProvider.getApplicationContext<Application>().getString(R.string.skip_to_program_start)
        compose.onNodeWithTag("recording-skip-prompt").assertTextEquals(label)
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals(listOf(start), prompt.skips)
    }

    @Test fun theFocusedPromptUsesTheStandardFocusedThemeAndTheRingDrainsClockwise() {
        val state = prompt(Color.White)
        state.offer(ad)
        advance(3_000L) // about half full, after the helper's two-second offer settle
        val bounds = compose.onNodeWithTag("recording-skip-countdown", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(state.view.width, state.view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { state.view.draw(Canvas(bitmap)) }
        try {
            fun pixel(x: Float, y: Float) = bitmap.getPixel((bounds.left + bounds.width * x).toInt(), (bounds.top + bounds.height * y).toInt())
            assertEquals("standard focused container, not a custom white", state.containerColor, pixel(.5f, .82f))
            assertEquals("standard focused content", state.contentColor, pixel(.45f, .5f))
        } finally {
            bitmap.recycle()
        }
        val (left, right) = ringContrast(state)
        assertTrue("remaining left half: $left", left > .8f)
        assertEquals("clockwise-drained right half shows only the track", .2f, right, .12f)
    }

    @Test fun aPressHeldFromBeforeThePromptCannotActivateIt() {
        val prompt = prompt()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        prompt.offer(RecordingMark(RecordingMarkKind.PROGRAM_START, 1_000_000L))
        assertEquals(listOf("recording-skip-prompt"), compose.focusedTags())
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.settleFrames(20)
        assertEquals(emptyList<RecordingMark>(), prompt.skips)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        prompt.offer(ad)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        assertEquals("release must not activate the replacement prompt", emptyList<RecordingMark>(), prompt.skips)
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals(listOf(ad), prompt.skips)
    }

    @Test fun theGlyphHasClearanceAndItsInkIsOpticallyCentredInsideTheRing() {
        val state = prompt()
        state.offer(ad)
        val ring = compose.onNodeWithTag("recording-skip-countdown", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("recording-skip-prompt").fetchSemanticsNode().boundsInRoot
        assertEquals("unchanged layout height", 40f, button.height, .01f)
        val scale = ring.height / 20f
        val bitmap = Bitmap.createBitmap(state.view.width, state.view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { state.view.draw(Canvas(bitmap)) }
        try {
            val base = android.graphics.Color.red(state.containerColor)
            var mass = 0f
            var momentX = 0f
            var momentY = 0f
            var minX = bitmap.width
            var maxX = 0
            var minY = bitmap.height
            var maxY = 0
            for (y in (ring.top + ring.height * .25f).toInt()..(ring.bottom - ring.height * .25f).toInt()) {
                for (x in (ring.left + ring.width * .25f).toInt()..(ring.right - ring.width * .25f).toInt()) {
                    val ink = (base - android.graphics.Color.red(bitmap.getPixel(x, y))).coerceAtLeast(0).toFloat()
                    mass += ink
                    momentX += (x + .5f) * ink
                    momentY += (y + .5f) * ink
                    if (ink > 90f) {
                        minX = minOf(minX, x); maxX = maxOf(maxX, x)
                        minY = minOf(minY, y); maxY = maxOf(maxY, y)
                    }
                }
            }
            assertTrue("glyph ink exists", mass > 0f)
            assertEquals("optical horizontal centre", ring.center.x, momentX / mass, .75f)
            assertEquals("optical vertical centre", ring.center.y, momentY / mass, .75f)
            assertEquals("8.5 dp glyph", 8.5f * scale, (maxY - minY + 1).toFloat(), 1f)
            val clearance = minOf(minX - ring.left, ring.right - maxX - 1) - 2f * scale
            assertTrue("glyph stays clear of the ring: $clearance", clearance >= 2.5f * scale)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun thePromptLeavesAfterTenSeconds() {
        val prompt = prompt()
        prompt.offer(ad)
        advance(SKIP_PROMPT_MS - 3_000L)
        assertEquals(emptyList<RecordingMark>(), prompt.timeouts)
        assertTrue(compose.tagExists("recording-skip-prompt"))
        advance(3_200L)
        assertEquals(listOf(ad), prompt.timeouts)
        assertFalse(compose.tagExists("recording-skip-prompt"))
    }

    @Test fun clearingTheTimedOutOfferKeepsTheEmptyRingDuringExit() {
        val state = prompt()
        state.offer(ad)
        compose.mainClock.advanceTimeBy(SKIP_PROMPT_MS - 3_000L, ignoreFrameDuration = true)
        compose.waitForIdle()
        while (state.timeouts.isEmpty()) advance(16L)
        assertEquals(null, state.prompt) // the real callback clears, not merely hides, the offer
        advance(48L)
        val (left, right) = ringContrast(state)
        assertEquals("left stays drained, not reset to full", .2f, left, .12f)
        assertEquals("right stays drained", .2f, right, .12f)
        advance(200L)
        assertFalse(compose.tagExists("recording-skip-prompt"))
    }

    @Test fun withdrawingAHalfElapsedOfferRetainsItsPartialRingDuringExit() {
        val state = prompt()
        state.offer(ad)
        advance(3_000L)
        val before = ringContrast(state)
        compose.runOnIdle { state.prompt = null }
        advance(48L)
        val after = ringContrast(state)
        assertEquals("remaining arc survives the fade", before.first, after.first, .12f)
        assertEquals("drained arc survives the fade", before.second, after.second, .12f)
        advance(200L)
        assertFalse(compose.tagExists("recording-skip-prompt"))
    }

    @Test fun nativeFocusOverflowIsNotClippedByTheExitFade() {
        val state = prompt()
        state.offer(ad)
        compose.runOnIdle { state.prompt = null }
        advance(80L)
        val bounds = compose.onNodeWithTag("recording-skip-prompt", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(state.view.width, state.view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { state.view.draw(Canvas(bitmap)) }
        try {
            val above = android.graphics.Color.red(bitmap.getPixel(bounds.center.x.toInt(), bounds.top.toInt() - 1))
            assertTrue("native focus growth above the layout must survive the fade layer: $above", above > 30)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun theEntryRiseDoesNotClipTheButtonAtTheAnchor() {
        val state = prompt()
        compose.runOnIdle { state.prompt = ad }
        var sawEntry = false
        repeat(10) {
            advance(16L)
            if (compose.tagExists("recording-skip-prompt")) {
                val bounds = compose.onNodeWithTag("recording-skip-prompt").fetchSemanticsNode().boundsInRoot
                assertTrue("the rising 40 dp button stays whole: $bounds", bounds.height >= 39.9f)
                val bitmap = Bitmap.createBitmap(state.view.width, state.view.height, Bitmap.Config.ARGB_8888)
                compose.runOnIdle { state.view.draw(Canvas(bitmap)) }
                try {
                    val top = android.graphics.Color.red(bitmap.getPixel(bounds.center.x.toInt(), bounds.top.toInt() + 5))
                    val bottom = android.graphics.Color.red(bitmap.getPixel(bounds.center.x.toInt(), bounds.bottom.toInt() - 1))
                    if (top > 40) {
                        sawEntry = true
                        assertTrue("the rising lower edge is painted, not cut off: $bottom / $top", bottom > top * .3f)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        assertTrue("the prompt appeared during the entry", sawEntry)
    }

    @Test fun anEqualMarkReofferedDuringTheExitStartsANewFullTimeout() {
        val state = prompt()
        state.offer(ad)
        compose.mainClock.advanceTimeBy(SKIP_PROMPT_MS - 3_000L, ignoreFrameDuration = true)
        compose.waitForIdle()
        while (state.timeouts.isEmpty()) advance(16L)
        assertEquals(null, state.prompt)
        advance(32L) // re-offer before the 150 ms exit disposes the presentation
        compose.runOnIdle { state.prompt = ad.copy() }
        advance(64L)
        assertEquals("the equal mark does not time out immediately", listOf(ad), state.timeouts)
        val (left, right) = ringContrast(state)
        assertTrue("new offer resets to nearly full, not the departing empty ring: $left / $right", left > .8f && right > .8f)
        compose.mainClock.advanceTimeBy(SKIP_PROMPT_MS - 1_000L, ignoreFrameDuration = true)
        compose.waitForIdle()
        assertEquals("the new offer gets its own ten-second clock", listOf(ad), state.timeouts)
        advance(1_200L)
        assertEquals(listOf(ad, ad), state.timeouts)
    }

    @Test fun thePromptTimesOutOnlyAfterTenSecondsShownAcrossVisibilityChanges() {
        val state = prompt()
        compose.changeState {
            state.shown = false
            state.prompt = ad
        }
        compose.mainClock.advanceTimeBy(SKIP_PROMPT_MS + 200L)
        assertFalse(compose.tagExists("recording-skip-prompt"))
        assertEquals("the hidden offer must not time out", emptyList<RecordingMark>(), state.timeouts)

        compose.changeState { state.shown = true }
        compose.mainClock.advanceTimeBy(2_000L)
        assertTrue(compose.tagExists("recording-skip-prompt"))
        compose.changeState { state.shown = false }
        compose.mainClock.advanceTimeBy(SKIP_PROMPT_MS)
        assertFalse(compose.tagExists("recording-skip-prompt"))
        assertEquals(emptyList<RecordingMark>(), state.timeouts)

        compose.changeState { state.shown = true }
        compose.mainClock.advanceTimeBy(SKIP_PROMPT_MS - 2_100L)
        assertTrue(compose.tagExists("recording-skip-prompt"))
        assertEquals(emptyList<RecordingMark>(), state.timeouts)
        advance(200L)
        assertEquals(listOf(ad), state.timeouts)
    }

    @Test fun theOfferStandsBackWhilePausedBufferingOrUnseekable() {
        assertTrue(recordingSkipPromptShown(offered = true, playing = true, canSeek = true, presented = true))
        for ((state, input) in mapOf(
            "paused" to (false to true),
            "buffering" to (false to true),
            "unseekable" to (true to false),
        )) {
            assertFalse(state, recordingSkipPromptShown(true, input.first, input.second, presented = true))
        }
        assertFalse(recordingSkipPromptShown(offered = true, playing = true, canSeek = true, presented = false))
        assertFalse(recordingSkipPromptShown(offered = false, playing = true, canSeek = true, presented = true))
        val prompt = prompt()
        prompt.offer(ad)
        assertEquals(listOf("recording-skip-prompt"), compose.focusedTags())
        compose.changeState { prompt.shown = false }
        compose.settleFrames(2, frameMillis = 16)
        compose.onNodeWithTag("recording-skip-countdown", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recording-skip-prompt").assertDoesNotExist()
        compose.settleFrames(20)
        assertFalse(compose.tagExists("recording-skip-prompt"))
        assertFalse("recording-skip-prompt" in compose.focusedTags())
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals(emptyList<RecordingMark>(), prompt.skips)
        compose.runOnIdle { prompt.shown = true }
        compose.settleFrames(20)
        assertEquals(listOf("recording-skip-prompt"), compose.focusedTags())
        assertEquals(emptyList<RecordingMark>(), prompt.timeouts)
    }

    @Test fun allActionsReachTheSeekbarAndReturnDuringABreakAndSkipReturnsToPause() {
        val state = controls(breakPlaying = true)
        assertEquals(listOf("player-pause"), compose.focusedTags())
        assertTrue(compose.tagExists("recording-skip"))
        assertFalse(compose.tagExists("recording-skip-prompt"))
        val actions = listOf("player-pause", "player-stop", "recording-skip", "player-info", "player-settings")
        actions.forEachIndexed { index, action ->
            assertEquals(listOf(action), compose.focusedTags())
            compose.pressKey(Key.DirectionUp, settleFrames = 20)
            assertEquals(listOf("player-seekbar"), compose.focusedTags())
            compose.pressKey(Key.DirectionDown, settleFrames = 20)
            assertEquals(listOf(action), compose.focusedTags())
            if (index < actions.lastIndex) compose.pressKey(Key.DirectionRight, settleFrames = 20)
        }
        compose.pressKey(Key.DirectionLeft, settleFrames = 20)
        compose.pressKey(Key.DirectionLeft, settleFrames = 20)
        assertEquals(listOf("recording-skip"), compose.focusedTags())
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals(listOf(ad.endMs), state.seeks)
        assertFalse(compose.tagExists("recording-skip"))
        assertEquals(listOf("player-pause"), compose.focusedTags())
        assertEquals("the accepting OK does not toggle Pause", 0, state.pauseToggles)
    }

    @Test fun paddingSkipIsReachableAndOutsideSpansNoSkipExists() {
        val state = controls(breakPlaying = false, padding = true)
        assertEquals(listOf("player-pause"), compose.focusedTags())
        compose.pressKey(Key.DirectionRight, settleFrames = 20)
        compose.pressKey(Key.DirectionRight, settleFrames = 20)
        assertEquals(listOf("recording-skip"), compose.focusedTags())
        compose.onNodeWithTag("recording-skip").assertTextEquals(
            ApplicationProvider.getApplicationContext<Application>().getString(R.string.skip_to_program_start))
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals(listOf(1_000_000L), state.seeks)
        assertFalse(compose.tagExists("recording-skip"))
        assertEquals(listOf("player-pause"), compose.focusedTags())
        assertEquals("the accepting OK does not toggle Pause", 0, state.pauseToggles)
    }

    @Test fun aHeldControlsPressCannotActivateTheReplacementOrTogglePause() {
        val state = controls(breakPlaying = false, padding = true)
        compose.pressKey(Key.DirectionRight, settleFrames = 20)
        compose.pressKey(Key.DirectionRight, settleFrames = 20)
        assertEquals(listOf("recording-skip"), compose.focusedTags())
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.changeState { state.position = ad.startMs + 1 }
        compose.settleFrames(2, frameMillis = 16)
        assertEquals(listOf("recording-skip"), compose.focusedTags())
        compose.onNodeWithTag("recording-skip").assertTextEquals(
            ApplicationProvider.getApplicationContext<Application>().getString(R.string.skip_ad_break))
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        assertEquals("release must not activate the replacement action", emptyList<Long>(), state.seeks)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.changeState { state.position = ad.endMs }
        compose.settleFrames(2)
        assertEquals(listOf("player-pause"), compose.focusedTags())
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        assertEquals("release must not activate fallback Play/Pause", 0, state.pauseToggles)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, state.pauseToggles)
    }

    @Test fun marksKeepNearestEntryAndBothVerticalExitsReturnToTheSeekbarDuringABreak() {
        val state = controls(breakPlaying = true)
        compose.pressKey(Key.DirectionUp, settleFrames = 20)
        for (close in listOf(Key.DirectionUp, Key.DirectionDown)) {
            compose.pressKey(Key.DirectionUp, settleFrames = 20)
            assertEquals(listOf("recording-marker-target"), compose.focusedTags())
            assertEquals("nearest entry with earlier tie", ad.startMs, state.navigation.selectedMs)
            assertFalse(compose.tagExists("recording-skip-prompt"))
            compose.pressKey(Key.DirectionRight, settleFrames = 20)
            assertEquals(ad.endMs, state.navigation.selectedMs)
            compose.pressKey(Key.DirectionLeft, settleFrames = 20)
            assertEquals(ad.startMs, state.navigation.selectedMs)
            compose.pressKey(close, settleFrames = 20)
            assertFalse(state.navigation.open)
            assertEquals(listOf("player-seekbar"), compose.focusedTags())
        }
        compose.pressKey(Key.DirectionUp, settleFrames = 20)
        compose.pressKey(Key.DirectionRight, settleFrames = 20)
        compose.pressKey(Key.DirectionCenter, settleFrames = 20)
        assertEquals("controls-up skip seeks to the chosen ad end", listOf(ad.endMs), state.seeks)
        assertEquals(listOf("player-seekbar"), compose.focusedTags())
    }

    @Test fun closingMarksReturnsFocusImmediatelyAndCanReopenDuringTheExit() {
        val state = controls(breakPlaying = false)
        compose.pressKey(Key.DirectionUp, settleFrames = 20)
        compose.pressKey(Key.DirectionUp, settleFrames = 20)
        assertEquals(listOf("recording-marker-target"), compose.focusedTags())
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertFalse("Down closes the marks", state.navigation.open) }
        advance(32L) // apply the close and the focus restoration, well before the 150 ms exit
        compose.waitForIdle()
        assertEquals(listOf("player-seekbar"), compose.focusedTags())
        assertFalse("the exiting pill is no longer interactive", compose.tagExists("recording-marker-target"))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        advance(48L)
        assertEquals(listOf("recording-marker-target"), compose.focusedTags())
        compose.settleFrames(20)
        assertEquals(listOf("recording-marker-target"), compose.focusedTags())
    }

    private class PromptState {
        var action by mutableStateOf<RecordingSkipAction?>(null)
            private set
        var prompt: RecordingMark?
            get() = action?.mark
            set(value) { action = value?.let { RecordingSkipAction(it, it.endMs) } }
        var shown by mutableStateOf(true)
        val skips = mutableListOf<RecordingMark>()
        val timeouts = mutableListOf<RecordingMark>()
        lateinit var offer: (RecordingMark) -> Unit
        lateinit var view: View
        var containerColor: Int = 0
        var contentColor: Int = 0
    }

    private fun prompt(background: Color = Color.Black): PromptState {
        val state = PromptState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state.view = LocalView.current
            TVHeadendPlayerTheme {
                state.containerColor = MaterialTheme.colorScheme.inverseSurface.toArgb()
                state.contentColor = MaterialTheme.colorScheme.inverseOnSurface.toArgb()
                val root = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().background(background).focusRequester(root).focusable()) {
                    RecordingSkipPrompt(
                        prompt = state.action,
                        shown = state.shown,
                        onSkip = { state.skips += it.mark },
                        onTimeout = { state.timeouts += it.mark; state.prompt = null },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 48.dp, vertical = 32.dp),
                    )
                }
                LaunchedEffect(Unit) { root.requestFocus() }
            }
        }
        compose.settleFrames(20)
        state.offer = { mark ->
            compose.runOnIdle { state.prompt = mark }
            compose.settleFrames(20)
        }
        return state
    }

    /** Stroke contrast relative to the same frame's container and glyph, so exit alpha cancels. */
    private fun ringContrast(state: PromptState): Pair<Float, Float> {
        val bounds = compose.onNodeWithTag("recording-skip-countdown", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(state.view.width, state.view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { state.view.draw(Canvas(bitmap)) }
        try {
            fun red(x: Float, y: Float) = android.graphics.Color.red(bitmap.getPixel(
                (bounds.left + bounds.width * x).toInt(), (bounds.top + bounds.height * y).toInt(),
            )).toFloat()
            val base = red(.5f, .82f)
            val ink = listOf(.35f, .4f, .45f, .5f, .55f, .6f, .65f)
                .map { red(it, .5f) }.maxBy { kotlin.math.abs(base - it) }
            assertTrue("the exiting glyph is still visible", kotlin.math.abs(base - ink) > 10f)
            return (base - red(1f / 20f, .5f)) / (base - ink) to
                (base - red(1f - 1f / 20f, .5f)) / (base - ink)
        } finally {
            bitmap.recycle()
        }
    }

    private class ControlsState {
        val navigation = RecordingMarkerNavigation()
        val seeks = mutableListOf<Long>()
        var pauseToggles = 0
        var position by mutableStateOf(0L)
    }

    private fun controls(breakPlaying: Boolean, title: String = "Zeit im Bild", padding: Boolean = false): ControlsState {
        val state = ControlsState()
        state.position = if (breakPlaying) 1_800_000L else 900_000L
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(
                    positionMs = state.position,
                    info = fixtureRecordingInfo(title = title, positionMs = state.position),
                    marks = listOf(ad) + chapterMarks(900_000L) +
                        if (padding) listOf(RecordingMark(RecordingMarkKind.PROGRAM_START, 1_000_000L)) else emptyList(),
                    markerNavigation = state.navigation,
                    onSeekMarker = { state.seeks += it; state.position = it },
                    onSkip = {
                        val destination = if (it.kind == RecordingMarkKind.PROGRAM_START) 1_000_000L else ad.endMs
                        state.seeks += destination
                        state.position = destination
                    },
                    onTogglePause = { state.pauseToggles++ },
                )
            }
        }
        compose.settleFrames(20)
        return state
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

}
