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
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * test clock stepped by hand. Three key frames of the skip prompt's countdown ring (first visible,
 * 5 s, 10 s) are saved at 960×540 in ignored `build/outputs/marks-motion-captures`; the prompt's
 * size and ring checks run with them.
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

    // Skip prompt over hidden controls during an ad break: three key frames, with the focus and size checks.
    @Test fun t08SkipPrompt() {
        scene(mode = PlayerChromeMode.HIDDEN, position = AD_MIDDLE)
        record("08", "skip-prompt", cropBottom = true) { r ->
            change { promptMark = AD; promptShown = true }
            val shownAt = compose.mainClock.currentTime
            compose.mainClock.advanceTimeBy(64L, ignoreFrameDuration = true)
            compose.waitForIdle()
            r.snap("ring-00000", "0%: first visible entry frame (64 ms)")
            // Let the entry settle (as the original 400 ms run did) so the native focus scale has finished.
            compose.mainClock.advanceTimeBy(336L, ignoreFrameDuration = true)
            compose.waitForIdle()
            r.note("focused after appear: ${focused()}")
            val bounds = compose.onNodeWithTag("recording-skip-prompt").fetchSemanticsNode().boundsInRoot
            assertTrue("prompt stays at most 44 dp high", bounds.height / 2f <= 44f)
            val ring = compose.onNodeWithTag("recording-skip-countdown", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("20 dp ring with native 1.1 focus scale", 22f, ring.height / 2f, .1f)
            r.note("prompt layout bounds at density 2: ${bounds.width / 2f} x ${bounds.height / 2f} dp; native focus scale is drawn inside the Surface")
            // The zero sample is the first visible entry frame, not a fabricated paused countdown.
            // The remaining samples are measured from the offer; the clock starts at its first frame.
            for (second in listOf(5_000L, 10_000L)) {
                val delta = second - (compose.mainClock.currentTime - shownAt)
                compose.mainClock.advanceTimeBy(delta, ignoreFrameDuration = true)
                compose.waitForIdle()
                r.snap("ring-${second.toString().padStart(5, '0')}", "elapsed ${second / 100f}%")
            }
            while (promptMark != null) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            r.note("onTimeout cleared the offer")
        }
    }

    // ---- scene -----------------------------------------------------------------------------

    private fun scene(
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        position: Long = POSITION,
    ) {
        this.mode = mode
        this.position = position
        val still = FixtureArt.art("still-ridge-light")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(still.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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

        fun note(text: String) { notes += text }

        /** Saves the current draw; returns whether it equals the previous snap. */
        fun snap(fileLabel: String, caption: String): Boolean {
            check(compose.onAllNodes(hasTestTag("recording-skip"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                "resting Skip must never be composed: $number $fileLabel"
            }
            val full = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(full)) }
            val half = Bitmap.createScaledBitmap(full, 960, 540, true)
            full.recycle()
            val pixels = IntArray(960 * 540).also { half.getPixels(it, 0, 960, 0, 0, 960, 540) }
            val same = last?.contentEquals(pixels) == true
            last = pixels
            File(dir, "frame-$fileLabel.png").outputStream().use { half.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tiles += caption to half
            return same
        }

        fun finish() {
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
                else "full 960x540 frames, tiles 50%") + " — key frames",
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
