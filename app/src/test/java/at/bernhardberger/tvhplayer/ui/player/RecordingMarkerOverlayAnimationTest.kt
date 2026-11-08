package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Hint focus and size changes retain the departing drawing and reverse from its current alpha. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingMarkerOverlayAnimationTest {
    @get:Rule val compose = createComposeRule()
    private var hints by mutableStateOf(false)
    private val navigation = RecordingMarkerNavigation()
    private var stops = listOf(1_000L, 7_000L).map { time ->
        RecordingMarkStop(time, RecordingMark(RecordingMarkKind.CHAPTER, time))
    }
    private var trackWidth = 800
    private var durationMs = 10_000L

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.width(trackWidth.dp).height(160.dp).background(Color.Black).testTag("hint-scene")
                    .onPreviewKeyEvent {
                        navigation.handle(it, stops.map { stop -> stop.positionMs }, {})
                    }) {
                    RecordingMarkerOverlay(navigation, stops, {}, durationMs, hints,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp))
                }
            }
        }
        advance(300)
    }

    @Test fun aFifteenMinuteHintBesideTheStartPillKeepsItsInk() = paddedStartHint(119 * 60_000L)

    @Test fun aFifteenMinuteHintAlsoFitsOnANinetyMinuteRecording() = paddedStartHint(90 * 60_000L, 960)

    private fun paddedStartHint(duration: Long, width: Int = 720) {
        // A 960 dp canvas leaves the track narrower after the endpoint labels/insets.
        trackWidth = width
        durationMs = duration
        stops = listOf(RecordingMarkStop(0), RecordingMarkStop(900_000,
            RecordingMark(RecordingMarkKind.CHAPTER, 900_000)))
        hints = true
        show()
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        repeat(30) { advance(16); drawFrame() }
        val pill = compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode().boundsInRoot
        val centre = (trackWidth * 900_000L / durationMs).toInt()
        assertTrue("the dot fits beyond the drawn pill plus 5 dp: $pill", pill.right + 5 < centre - 3)
        assertTrue("15:00 dot must not be hidden by transparent overflow padding", ink(centre) > 0f)
    }

    @Test fun openHintsUseSeventyPercentInkAndProgramBoundariesHaveDots() {
        stops = listOf(RecordingMarkStop(1_000, RecordingMark(RecordingMarkKind.CHAPTER, 1_000)),
            RecordingMarkStop(7_000, RecordingMark(RecordingMarkKind.PROGRAM_START, 7_000)))
        hints = true
        show()
        assertTrue("program boundary has a resting dot", ink() > 0f)
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        repeat(30) { advance(16); drawFrame() }
        val pixels = compose.onNodeWithTag("hint-scene").captureToImage().toPixelMap()
        // Theme onSurface is #E1E2E5, not pure white.
        assertEquals("open dot alpha", .70f * 225 / 255, pixels[560, 128].red, .02f)
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    /** Ink around the second chapter, which is never the selected/occluded one. */
    private fun ink(centreX: Int = 560): Float {
        val pixels = compose.onNodeWithTag("hint-scene").captureToImage().toPixelMap()
        var sum = 0f
        for (x in centreX - 6 until centreX + 6) for (y in 122..133) sum += pixels[x, y].red
        return sum
    }

    @Test fun focusHintsFadeInOutAndReverseWithoutDroppingTheDepartingSet() {
        show()
        assertEquals(0f, ink(), 0.01f)
        compose.runOnIdle { hints = true }
        advance(32); advance(48)
        val entering = ink()
        assertTrue(entering > 0f)
        advance(200)
        val focused = ink()
        assertTrue("intermediate fade", entering < focused)
        compose.runOnIdle { hints = false }
        advance(32); advance(48)
        val leaving = ink()
        assertTrue("departing hints remain drawn", leaving > 0f && leaving < focused)
        compose.runOnIdle { hints = true }
        advance(16)
        assertTrue("reversal starts from the current drawing", ink() > 0f)
        advance(250)
        assertEquals(focused, ink(), 0.3f)
    }

    @Test fun openSizeCrossfadesAndAnInterruptedCloseReturnsToTheFocusedSize() {
        hints = true
        show()
        val small = ink()
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        advance(32); advance(48)
        val growing = ink()
        advance(250)
        val large = ink()
        assertTrue("both size drawings contribute mid-fade", growing > small && growing < large)
        compose.runOnIdle { navigation.dismiss() }
        advance(32); advance(48)
        val shrinking = ink()
        assertTrue(shrinking > small && shrinking < large)
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        advance(32)
        assertTrue(ink() > small)
        advance(300)
        assertEquals(large, ink(), 0.01f)
    }

    @Test fun chapterInkStaysCentredThroughBothSizeCrossfades() = sizeCrossfadeKeepsCentre(RecordingMarkKind.CHAPTER)

    @Test fun scissorsInkStaysCentredThroughBothSizeCrossfades() = sizeCrossfadeKeepsCentre(RecordingMarkKind.CUT)

    private fun sizeCrossfadeKeepsCentre(kind: RecordingMarkKind) {
        stops = listOf(1_000L, 7_000L).map { time -> RecordingMarkStop(time, RecordingMark(kind, time)) }
        hints = true
        show()
        fun centroid(): Pair<Float, Float> {
            val pixels = compose.onNodeWithTag("hint-scene").captureToImage().toPixelMap()
            var mass = 0f
            var xSum = 0f
            var ySum = 0f
            for (x in 548..571) for (y in 116..139) {
                val weight = pixels[x, y].red
                mass += weight; xSum += x * weight; ySum += y * weight
            }
            assertTrue("symbol ink is present", mass > 0f)
            return xSum / mass to ySum / mass
        }
        val small = centroid()
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        repeat(8) {
            advance(16)
            val mid = centroid()
            assertEquals("$kind arriving x at ${it * 16} ms", small.first, mid.first, .5f)
            assertEquals("$kind arriving y at ${it * 16} ms", small.second, mid.second, .5f)
        }
        advance(250)
        val large = centroid()
        compose.runOnIdle { navigation.dismiss() }
        repeat(8) {
            advance(16)
            val mid = centroid()
            assertEquals("$kind departing x at ${it * 16} ms", large.first, mid.first, .5f)
            assertEquals("$kind departing y at ${it * 16} ms", large.second, mid.second, .5f)
        }
        advance(250)
        val closed = centroid()
        assertEquals(small.first, closed.first, .01f)
        assertEquals(small.second, closed.second, .01f)
    }

    @Test fun aNeighbourOutsideTheDrawnPillStaysVisibleDuringTravel() {
        stops = listOf(1_000L, 1_500L, 7_000L).map { time ->
            RecordingMarkStop(time, RecordingMark(RecordingMarkKind.CHAPTER, time))
        }
        hints = true
        show()
        assertTrue(ink(120) > 0f)
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        // Subcomposed transition children must be measured each frame, as on a real display.
        repeat(6) { advance(16); drawFrame() }
        val thinning = ink(120)
        val unblocked = ink()
        // Opening crossfades to a larger symbol: compare peers at the same animated size,
        // not the smaller pre-opening symbol whose ink can be lower even without thinning.
        assertEquals("transparent overflow must not thin a neighbour", unblocked, thinning, .1f)
        advance(250)
        assertTrue(ink(120) > 0f)
        compose.runOnIdle { navigation.move(stops.map { it.positionMs }, 2) }
        advance(96)
        assertTrue("moving the pill away keeps the neighbour", ink(120) > 0f)
        compose.runOnIdle { navigation.move(stops.map { it.positionMs }, -2) }
        advance(350)
        assertTrue("reversing travel does not hide a fitting neighbour", ink(120) > 0f)
    }

    /** The incoming name has a blank centre, so its ink cannot masquerade as the outgoing bar. */
    @Test fun aSingleRightStartsTheLabelFadeWithTravel() {
        stops = listOf("|", "Left    Right").mapIndexed { index, name ->
            val time = (index + 1) * 1_000L
            RecordingMarkStop(time, RecordingMark(RecordingMarkKind.CHAPTER, time, name = name))
        }
        hints = true
        show()
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        repeat(25) { advance(16) }
        val oldInk = nameInk(centre = true)
        assertTrue(oldInk > 0f)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        // Pump every frame, as the motion capture does; no auto-advance/settle hides a late target.
        drawFrame()
        val frames = mutableListOf<Triple<Float, Float, Float>>()
        repeat(17) { frame ->
            if (frame > 0) { advance(16); drawFrame() }
            val centre = compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode().boundsInRoot.center.x
            val old = nameInk(centre = true)
            val new = nameInk(centre = false)
            frames += Triple(centre, old, new)
        }
        val firstMove = frames.indexOfFirst { kotlin.math.abs(it.first - frames.first().first) > .5f }
        assertTrue("pill moved", firstMove >= 0)
        val firstFade = frames.indexOfFirst { it.second < oldInk * .95f }
        assertEquals("travel and fade share their first advancing frame", firstMove, firstFade)
        assertTrue("old label fades on first moving frame ${firstMove * 16}: ${frames[firstMove].second} / $oldInk",
            frames[firstMove].second < oldInk * .95f)
        // The preceding frame is animation play-time zero (no displacement/alpha change).
        // Native test capture timestamps include input/apply/start frames; they are not
        // animation play time. Assert the tokens on the shared measured animation clock.
        val animationStartFrame = firstMove - 1
        val at64 = frames[animationStartFrame + 4]
        val at96 = frames[animationStartFrame + 6]
        assertTrue("settled incoming label must draw", frames.last().third > 0f)
        assertTrue("outgoing label gone by animation 64 ms: ${at64.second} / $oldInk", at64.second < oldInk * .05f)
        assertTrue("incoming label reaches half ink by animation 96 ms: ${at96.third} / ${frames.last().third}",
            at96.third >= frames.last().third * .5f)
    }

    /** The pill's container must not flash while it travels and its label swaps (moved from the capture matrix). */
    @Test fun thePillContainerColourStaysConstantThroughEveryTravelFrame() {
        stops = (1..3).map { index ->
            val time = index * 1_000L
            RecordingMarkStop(time, RecordingMark(RecordingMarkKind.CHAPTER, time, name = "Chapter $index"))
        }
        hints = true
        show()
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        repeat(25) { advance(16) }
        val container = containerColour()
        assertTrue("the sample must hit the drawn container, not the black scene: $container", container != Color.Black)
        for (key in listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionLeft)) {
            compose.onRoot().performKeyInput { pressKey(key) }
            drawFrame()
            val startX = compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode().boundsInRoot.center.x
            var moved = false
            repeat(25) { frame ->
                advance(16); drawFrame()
                moved = moved ||
                    kotlin.math.abs(compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode().boundsInRoot.center.x - startX) > .5f
                assertEquals("pill container changed at frame $frame of $key", container, containerColour())
            }
            assertTrue("the pill travelled on $key", moved)
        }
    }

    /** Top padding centre of the pill: no label ink, rounded edge or underlying scene. */
    private fun containerColour(): Color {
        val scene = compose.onNodeWithTag("hint-scene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode().boundsInRoot
        return scene.captureToImage().toPixelMap()[(pill.center.x - sceneBounds.left).toInt(), (pill.top - sceneBounds.top).toInt() + 2]
    }

    @Test fun rapidHeldSelectionsRetainTheLastLabelUntilWithinOneStop() {
        stops = (1..5).map { index ->
            val time = index * 1_000L
            RecordingMarkStop(time, RecordingMark(RecordingMarkKind.CHAPTER, time, name = "Chapter $index"))
        }
        hints = true
        show()
        compose.runOnIdle { navigation.show(stops.map { it.positionMs }, 0) }
        repeat(25) { advance(16) }
        compose.runOnIdle {
            // Three repeated moves before the next frame leave travel more than one stop behind.
            repeat(3) { navigation.move(stops.map { it.positionMs }, 1) }
        }
        drawFrame()
        repeat(2) { advance(16); drawFrame() }
        assertEquals(4_000L, navigation.selectedMs)
        val names = compose.onAllNodes(hasTestTag("recording-marker-name"), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text] }.map { it.text }
        assertEquals(listOf("Chapter 1"), names)
        val description = compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertTrue("description follows selection immediately: $description", description.contains("Chapter 4"))
        repeat(35) { advance(16) }
        val settledNames = compose.onAllNodes(hasTestTag("recording-marker-name"), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text] }.map { it.text }
        assertEquals(listOf("Chapter 4"), settledNames)
    }

    private fun nameInk(centre: Boolean): Float {
        val scene = compose.onNodeWithTag("hint-scene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithTag("recording-marker-target").fetchSemanticsNode().boundsInRoot
        val name = compose.onAllNodes(hasTestTag("recording-marker-name"), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        val pixels = scene.captureToImage().toPixelMap()
        val cx = (pill.center.x - sceneBounds.left).toInt()
        val top = (name.top - sceneBounds.top).toInt()
        val bottom = (name.bottom - sceneBounds.top).toInt()
        val halfWidth = (pill.width / 2).toInt() - 12
        var ink = 0f
        for (x in cx - halfWidth..cx + halfWidth) {
            val distance = kotlin.math.abs(x - cx)
            if (if (centre) distance > 2 else distance < 8) continue
            for (y in top until bottom) ink += (225f / 255f - pixels[x, y].red).coerceAtLeast(0f)
        }
        return ink
    }

    // SubcomposeLayout composes during measure: native Robolectric must draw each manual frame,
    // just as the capture recorder/real display does, rather than defer all layout until 96 ms.
    private fun drawFrame() {
        compose.onNodeWithTag("hint-scene").captureToImage()
    }
}
