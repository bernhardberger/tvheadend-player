package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import java.io.File
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design-review motion evidence for the recording player's seekbar and marks: the production
 * chrome ([PlayerChrome] through [RecordingChromeFixture]) and [RecordingSkipPrompt] over the
 * fixture still, on a 1920×1080 canvas (960×540 dp at density 2), English, font scale 1.0, with the
 * test clock stepped by hand: one frame (16 ms) at a time for the first 400 ms, then 50 ms until
 * two consecutive frames are identical (cap 1 s). Keys go through the real focus and key handlers.
 * Frames are saved at 960×540 in ignored `build/outputs/marks-motion-captures`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
class PlayerMarksMotionCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule(effectContext = kotlinx.coroutines.test.StandardTestDispatcher())
    private var defaultZone: TimeZone? = null
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()

    private lateinit var view: View
    private val navigation = RecordingMarkerNavigation()
    private var mode by mutableStateOf(PlayerChromeMode.CONTROLS)
    private var position by mutableStateOf(POSITION)
    private var adBreak by mutableStateOf<RecordingMark?>(null)
    private var target by mutableStateOf<Long?>(null)
    private var origin by mutableStateOf<Long?>(null)
    private var stepId by mutableStateOf(0)
    private var promptMark by mutableStateOf<RecordingMark?>(null)
    private var promptShown by mutableStateOf(false)
    private val steps = mutableListOf<Long>()

    @Before fun setup() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        images.install(FixtureArt.imageLoader(app, mapOf(
            PICON to FixtureArt.picon("ridge-earth"),
            ART to FixtureArt.art("ridge-light"),
        )))
    }

    @After fun restoreZone() {
        defaultZone?.let(TimeZone::setDefault)
    }

    // 1. Controls hidden -> controls shown.
    @Test fun t01ControlsReveal() {
        scene(mode = PlayerChromeMode.HIDDEN)
        record("01", "controls-reveal", cropBottom = false) { r ->
            change { mode = PlayerChromeMode.CONTROLS }
            r.run()
        }
    }

    // 2. Action row (Play/Pause) -> seekbar.
    @Test fun t02FocusSeekbar() {
        scene()
        assertFocused("player-pause")
        record("02", "focus-seekbar", cropBottom = true) { r ->
            press(Key.DirectionUp)
            r.run()
            r.note("focused after: ${focused()}")
        }
    }

    // 3. Seekbar -> action row.
    @Test fun t03UnfocusSeekbar() {
        scene()
        pressSettled(Key.DirectionUp)
        assertFocused("player-seekbar")
        record("03", "unfocus-seekbar", cropBottom = true) { r ->
            press(Key.DirectionDown)
            r.run()
            r.note("focused after: ${focused()}")
        }
    }

    // 4. Seekbar Left/Right: one step, then a held repeat of five.
    @Test fun t04SeekbarStep() {
        scene()
        pressSettled(Key.DirectionUp)
        assertFocused("player-seekbar")
        record("04", "seekbar-step", cropBottom = true) { r ->
            press(Key.DirectionRight)
            r.run(tag = "step")
            r.note("single step deltas: $steps")
            steps.clear()
            // Android repeats a held key every ~50 ms after its initial delay; frames are 16 ms,
            // so the repeats land every third frame (48 ms), then the key is released.
            r.run(tag = "held") { elapsed ->
                // The test library refuses a second keyDown, so dispatch Android key events with a
                // repeat count straight to the composition's view, as the framework does.
                if (elapsed % 48L == 0L && elapsed <= 192L) rightKey(android.view.KeyEvent.ACTION_DOWN, (elapsed / 48L).toInt())
                if (elapsed == 208L) rightKey(android.view.KeyEvent.ACTION_UP, 0)
            }
            r.note("held repeat deltas: $steps")
        }
    }

    // 5. Open the marks row from the seekbar (Up): the cut at 40:00 is the first stop.
    @Test fun t05OpenMarksOnCut() = openMarks("05", "open-marks-cut", pos = 2_300_000L)

    // 5b. The same on an ad break.
    @Test fun t05bOpenMarksOnAdBreak() = openMarks("05b", "open-marks-ad-break", pos = 1_000_000L)

    private fun openMarks(number: String, slug: String, pos: Long) {
        scene(position = pos)
        pressSettled(Key.DirectionUp)
        assertFocused("player-seekbar")
        record(number, slug, cropBottom = true) { r ->
            press(Key.DirectionUp)
            r.run()
            r.note("marks row open=${navigation.open} selectedMs=${navigation.selectedMs}; focused after: ${focused()}")
        }
    }

    // 6. Marks row: Right, Right, Left.
    @Test fun t06MarksTravel() {
        scene(position = 1_700_000L)
        pressSettled(Key.DirectionUp)
        pressSettled(Key.DirectionUp)
        assertFocused("recording-marker-target")
        record("06", "marks-travel", cropBottom = true) { r ->
            r.note("opened on ${navigation.selectedMs}")
            press(Key.DirectionRight); r.run(tag = "R1"); r.note("after R1: ${navigation.selectedMs}")
            press(Key.DirectionRight); r.run(tag = "R2"); r.note("after R2: ${navigation.selectedMs}")
            press(Key.DirectionLeft); r.run(tag = "L1"); r.note("after L1: ${navigation.selectedMs}")
        }
    }

    // 7. Close the marks row (Down), back to the seekbar. Back is the screen's interceptor, not the chrome.
    @Test fun t07CloseMarks() {
        scene(position = 2_300_000L)
        pressSettled(Key.DirectionUp)
        pressSettled(Key.DirectionUp)
        assertFocused("recording-marker-target")
        record("07", "close-marks", cropBottom = true) { r ->
            press(Key.DirectionDown)
            r.run()
            r.note("open=${navigation.open}; focused after: ${focused()}")
        }
    }

    // 8. Skip prompt over hidden controls during an ad break.
    @Test fun t08SkipPrompt() = skipPrompt("08", "skip-prompt", false)

    @Test fun t08bSkipPromptOnDarkVideo() = skipPrompt("08b", "skip-prompt-dark", true)

    private fun skipPrompt(number: String, slug: String, darkVideo: Boolean) {
        scene(mode = PlayerChromeMode.HIDDEN, position = AD_MIDDLE, darkVideo = darkVideo)
        record(number, slug, cropBottom = true) { r ->
            change { promptMark = AD; promptShown = true }
            val shownAt = compose.mainClock.currentTime
            r.run(tag = "appear", maxMs = 400L, stopWhenStill = false)
            r.note("focused after appear: ${focused()}")
            // Fill samples, measured from the offer (the prompt's own clock starts at its first frame).
            for (second in listOf(1_000L, 2_500L, 5_000L, 7_500L, 9_500L, 10_000L)) {
                val delta = second - (compose.mainClock.currentTime - shownAt)
                compose.mainClock.advanceTimeBy(delta, ignoreFrameDuration = true)
                compose.waitForIdle()
                r.snap("fill-${second.toString().padStart(5, '0')}", "fill ${second / 1000.0} s")
            }
            while (promptMark != null) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            r.note("onTimeout cleared the offer; departing fill is retained during exit")
            r.run(tag = "exit", maxMs = 400L, stopWhenStill = false)
        }
    }

    // 9. Resting Skip with controls up during an ad break.
    @Test fun t09RestingSkip() {
        scene(mode = PlayerChromeMode.HIDDEN, position = AD_MIDDLE, adBreak = AD)
        record("09", "resting-skip", cropBottom = false) { r ->
            // A: controls appear while the break plays.
            change { mode = PlayerChromeMode.CONTROLS }
            r.run(tag = "A-reveal")
            settle()
            // B: the break starts while the controls are up.
            change { adBreak = null }
            settle()
            change { adBreak = AD }
            r.run(tag = "B-break-starts")
            settle()
            // C: Up from Settings focuses Skip.
            repeat(6) { if (focused() != listOf("player-settings")) pressSettled(Key.DirectionRight) }
            assertFocused("player-settings")
            press(Key.DirectionUp)
            r.run(tag = "C-up-from-settings")
            r.note("focused after C: ${focused()}")
        }
    }

    // ---- scene -----------------------------------------------------------------------------

    private fun scene(
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        position: Long = POSITION,
        adBreak: RecordingMark? = null,
        darkVideo: Boolean = false,
    ) {
        this.mode = mode
        this.position = position
        this.adBreak = adBreak
        val still = FixtureArt.art("still-ridge-light")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(still.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (darkVideo) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)))
                        ProvideArtworkSession(session) {
                            RecordingChromeFixture(
                                mode = this@PlayerMarksMotionCaptureTest.mode,
                                positionMs = this@PlayerMarksMotionCaptureTest.position,
                                durationMs = DURATION,
                                targetMs = target,
                                originMs = origin,
                                stepId = stepId,
                                info = fixtureRecordingInfo("Ridge Light", subtitle = "Winter on the ridge",
                                    positionMs = this@PlayerMarksMotionCaptureTest.position, durationMs = DURATION)
                                    .copy(channelName = "Ridge Earth HD", channelNumber = "101"),
                                picon = PICON,
                                artwork = ART,
                                marks = MIXED,
                                markerNavigation = navigation,
                                adBreak = this@PlayerMarksMotionCaptureTest.adBreak,
                                onSeek = { delta ->
                                    steps += delta
                                    origin = origin ?: this@PlayerMarksMotionCaptureTest.position
                                    target = ((target ?: this@PlayerMarksMotionCaptureTest.position) + delta).coerceIn(0L, DURATION)
                                    stepId += 1
                                },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                        RecordingSkipPrompt(
                            prompt = promptMark,
                            shown = promptShown,
                            onSkip = { promptMark = null },
                            onTimeout = { promptMark = null },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = TvGridHorizontalMargin, bottom = TvGridVerticalMargin),
                        )
                    }
                }
            }
        }
        settle()
    }

    private fun change(block: () -> Unit) {
        compose.runOnIdle {
            block()
            Snapshot.sendApplyNotifications()
        }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun rightKey(action: Int, repeat: Int) {
        compose.runOnUiThread {
            view.dispatchKeyEvent(android.view.KeyEvent(0L, compose.mainClock.currentTime, action,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat))
        }
        compose.waitForIdle()
    }

    private fun pressSettled(key: Key) {
        press(key)
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        repeat(15) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun focused(): List<String> =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun assertFocused(tag: String) {
        val now = focused()
        check(tag in now) { "expected $tag focused, focused: $now" }
    }

    @Suppress("unused") private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // ---- recording -------------------------------------------------------------------------

    private fun record(number: String, slug: String, cropBottom: Boolean, body: (Recorder) -> Unit) {
        val recorder = Recorder(number, slug, cropBottom)
        try { body(recorder) } finally { recorder.finish() }
    }

    private inner class Recorder(val number: String, val slug: String, val cropBottom: Boolean) {
        val dir = File(OUT, "$number-$slug").apply { deleteRecursively(); mkdirs() }
        private val tiles = mutableListOf<Pair<String, Bitmap>>()
        private val notes = mutableListOf<String>()
        private var last: IntArray? = null
        private val containerColours = mutableMapOf<String, Int>()

        fun note(text: String) { notes += text }

        /** Saves the current draw; returns whether it equals the previous snap. */
        fun snap(fileLabel: String, caption: String): Boolean {
            val full = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(full)) }
            if (number == "06") {
                val bounds = compose.onAllNodes(hasTestTag("recording-marker-target"), useUnmergedTree = true)
                    .fetchSemanticsNodes().single().boundsInRoot
                // Centre of the top padding: no label ink, rounded edge or underlying video.
                val colour = full.getPixel(bounds.center.x.toInt(), bounds.top.toInt() + 4)
                val sequence = fileLabel.substringBefore('-')
                val expected = containerColours.getOrPut(sequence) { colour }
                check(colour == expected) {
                    "$fileLabel pill container changed: ${colour.toUInt().toString(16)} vs ${expected.toUInt().toString(16)}"
                }
            }
            val half = Bitmap.createScaledBitmap(full, 960, 540, true)
            full.recycle()
            val pixels = IntArray(960 * 540).also { half.getPixels(it, 0, 960, 0, 0, 960, 540) }
            val same = last?.contentEquals(pixels) == true
            last = pixels
            File(dir, "frame-$fileLabel.png").outputStream().use { half.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tiles += caption to half
            return same
        }

        /**
         * Frames from now: 16 ms steps to 400 ms, then 50 ms steps until two consecutive frames
         * match (or [maxMs]). [beforeSnap] runs at each elapsed time before its frame is taken.
         */
        fun run(
            tag: String = "",
            maxMs: Long = 1_000L,
            stopWhenStill: Boolean = true,
            beforeSnap: (Long) -> Unit = {},
        ) {
            val t0 = compose.mainClock.currentTime
            fun elapsed() = compose.mainClock.currentTime - t0
            fun frame(): Boolean {
                val ms = elapsed()
                val label = (if (tag.isEmpty()) "" else "$tag-") + ms.toString().padStart(4, '0')
                return snap(label, (if (tag.isEmpty()) "" else "$tag ") + "$ms ms")
            }
            beforeSnap(0L)
            compose.waitForIdle()
            frame()
            while (elapsed() < minOf(400L, maxMs)) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                beforeSnap(elapsed())
                compose.waitForIdle()
                frame()
            }
            while (elapsed() < maxMs && maxMs > 400L) {
                compose.mainClock.advanceTimeBy(50L, ignoreFrameDuration = true)
                compose.waitForIdle()
                beforeSnap(elapsed())
                compose.waitForIdle()
                if (frame() && stopWhenStill) break
            }
        }

        fun finish() {
            containerColours.forEach { (sequence, colour) ->
                note("$sequence container pixels constant throughout: #${colour.toUInt().toString(16)}")
            }
            File(dir, "notes.txt").writeText(notes.joinToString("\n"))
            if (tiles.isEmpty()) return
            val columns = if (cropBottom) 3 else 4
            val tileWidth = if (cropBottom) 640 else 480
            val source = if (cropBottom) Rect(0, 297, 960, 540) else Rect(0, 0, 960, 540)
            val tileHeight = tileWidth * source.height() / source.width()
            val label = 22
            val title = 30
            val rows = (tiles.size + columns - 1) / columns
            val sheet = Bitmap.createBitmap(columns * tileWidth, title + rows * (tileHeight + label), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(24, 24, 28))
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 16f }
            val filter = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawText(
                "$number $slug — " + (if (cropBottom) "bottom 45% of the 960x540 frames (y 297..540), tiles 67%"
                else "full 960x540 frames, tiles 50%") + " — 16 ms steps to 400 ms, then ~50 ms",
                8f, 20f, text,
            )
            tiles.forEachIndexed { index, (caption, bitmap) ->
                val left = (index % columns) * tileWidth
                val top = title + (index / columns) * (tileHeight + label)
                canvas.drawText(caption, left + 6f, top + 16f, text)
                canvas.drawBitmap(bitmap, source, Rect(left, top + label, left + tileWidth - 2, top + label + tileHeight - 2), filter)
                bitmap.recycle()
            }
            File(OUT, "$number-$slug-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }

    private companion object {
        val OUT = File("build/outputs/marks-motion-captures").apply { mkdirs() }
        val PICON = ArtworkId(1)
        val ART = ArtworkId(2)
        const val DURATION = 4_350_000L
        const val POSITION = 1_045_000L
        const val AD_MIDDLE = 1_290_000L

        /** 1:12:30: a played cut, a named chapter, two ad breaks, an unplayed cut, chapters. */
        val MIXED = listOf(
            RecordingMark(RecordingMarkKind.CUT, 300_000L, 420_000L),
            RecordingMark(RecordingMarkKind.CHAPTER, 600_000L, name = "The long climb to the northern ridge before the first winter storm"),
            RecordingMark(RecordingMarkKind.AD_BREAK, 1_140_000L, 1_440_000L),
            RecordingMark(RecordingMarkKind.CHAPTER, 1_800_000L),
            RecordingMark(RecordingMarkKind.CUT, 2_400_000L, 2_520_000L),
            RecordingMark(RecordingMarkKind.AD_BREAK, 3_000_000L, 3_300_000L),
            RecordingMark(RecordingMarkKind.CHAPTER, 3_600_000L, name = "Night on the ridge"),
        )
        val AD = MIXED.first { it.kind == RecordingMarkKind.AD_BREAK }
    }
}
