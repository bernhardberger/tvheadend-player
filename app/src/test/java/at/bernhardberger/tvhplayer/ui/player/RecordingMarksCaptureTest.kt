package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import at.bernhardberger.tvhplayer.core.recordingMarkStops
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import java.io.File
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recording marks evidence: the production recording chrome over a still at the 960×540 dp
 * canvas with ad breaks, cuts and chapters on the timeline — the Banner's passive bar, the
 * focused seekbar's hint symbols, dense thinning, the marks row on each kind, a chapters-only
 * recording and a recording without a channel. English, font scale 1.0, fictional fixture art.
 * Writes to build/outputs/recording-marks (ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
class RecordingMarksCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule(effectContext = kotlinx.coroutines.test.StandardTestDispatcher())
    private var defaultZone: TimeZone? = null
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()

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

    /** The Banner's passive bar: ad tint, dashed cut ticks and chapter ticks, no symbols. */
    @Test fun passive() = capture("passive", MIXED, mode = PlayerChromeMode.BANNER)

    /** The focused seekbar: dimmed hint symbols above every mark start. */
    @Test fun focusedHints() = capture("focused-hints", MIXED, focus = "player-seekbar")

    /** A four-hour recording with a chapter every two minutes and six ad breaks: symbols thin, ticks stay. */
    @Test fun denseThinning() = capture("dense-thinning", DENSE, durationMs = 14_400_000L,
        positionMs = 5_000_000L, focus = "player-seekbar")

    /** Dense marks with the row open on an ad break among chapters. */
    @Test fun denseOpen() = capture("dense-open", DENSE, durationMs = 14_400_000L,
        positionMs = 5_000_000L, focus = "player-seekbar", openAt = 5_460_000L)

    @Test fun openAdBreak() = capture("open-ad-break", MIXED, focus = "player-seekbar", openAt = 1_140_000L, name = "Ad break")
    @Test fun openCut() = capture("open-cut", MIXED, focus = "player-seekbar", openAt = 2_400_000L, name = "Cut")
    /** The row on an ad break's and a cut's end: the stop to skip to by hand, the interval still selected. */
    @Test fun openAdBreakEnd() = capture("open-ad-break-end", MIXED, focus = "player-seekbar", openAt = 1_440_000L,
        name = "Ads end")
    @Test fun openCutEnd() = capture("open-cut-end", MIXED, focus = "player-seekbar", openAt = 2_520_000L, name = "Cut ends")
    @Test fun openPlayedCut() = capture("open-played-cut", MIXED, focus = "player-seekbar", openAt = 300_000L)
    @Test fun openNamedChapter() = capture("open-named-chapter", MIXED, focus = "player-seekbar", openAt = 600_000L)
    @Test fun openUnnamedChapter() = capture("open-unnamed-chapter", MIXED, focus = "player-seekbar", openAt = 1_800_000L)

    /** Chapters from the file only: hints appear as dots, the marks row opens on a named chapter. */
    @Test fun chaptersOnly() = capture("chapters-only", CHAPTERS, focus = "player-seekbar")
    @Test fun chaptersOnlyOpen() = capture("chapters-only-open", CHAPTERS, focus = "player-seekbar", openAt = 1_500_000L)

    /** A recording without a channel and without artwork: the dimmed recordings symbol in the picon panel. */
    @Test fun withoutChannel() = capture("without-channel", MIXED, focus = "player-pause", withoutChannel = true)

    private fun capture(
        scene: String,
        marks: List<RecordingMark>,
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        durationMs: Long = 4_350_000L,
        positionMs: Long = 1_045_000L,
        focus: String? = null,
        openAt: Long? = null,
        withoutChannel: Boolean = false,
        /** The open pill's name line, where the capture checks it. */
        name: String? = null,
    ) {
        val still = FixtureArt.art("still-ridge-light")
        val navigation = RecordingMarkerNavigation()
        lateinit var view: View
        var shown by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        Image(still.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (shown) ProvideArtworkSession(session) {
                            RecordingChromeFixture(
                                mode = mode,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                restoreFocus = focus,
                                info = fixtureRecordingInfo("Ridge Light", subtitle = "Winter on the ridge",
                                    positionMs = positionMs, durationMs = durationMs)
                                    .let { if (withoutChannel) it.copy(channelName = "", channelNumber = "")
                                        else it.copy(channelName = "Ridge Earth HD", channelNumber = "101") },
                                picon = PICON.takeUnless { withoutChannel },
                                artwork = ART.takeUnless { withoutChannel },
                                marks = marks,
                                withoutChannel = withoutChannel,
                                markerNavigation = navigation,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { shown = true }
        settle()
        if (focus != null) compose.onNodeWithTag(focus).assertIsFocused()
        if (openAt != null) {
            compose.runOnIdle { navigation.show(recordingMarkStops(marks, durationMs).map { it.positionMs }, openAt - 1) }
            settle()
            check(navigation.selectedMs == openAt) { "marks row opened on ${navigation.selectedMs}, not $openAt" }
            compose.onNodeWithTag("recording-marker-target").assertIsFocused()
            // A named pill is two lines tall; it stays clear of the info bar's subtitle above the timeline.
            val selected = requireNotNull(recordingMarkStops(marks, durationMs).first { it.positionMs == openAt }.mark)
            if (selected.kind != RecordingMarkKind.CHAPTER || selected.name != null) {
                val line = compose.onNodeWithTag("recording-marker-name", useUnmergedTree = true)
                if (name != null) line.assertTextEquals(name) else line.assertExists()
                val pill = compose.onNodeWithTag("recording-marker-target").getBoundsInRoot()
                val subtitle = compose.onNodeWithTag("player-info-subtitle", useUnmergedTree = true).getBoundsInRoot()
                assertTrue("pill top ${pill.top} overlaps the subtitle ending at ${subtitle.bottom}", pill.top >= subtitle.bottom)
            }
        }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/recording-marks").apply { mkdirs() }
        File(directory, "$scene-en-font1.0.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun settle() {
        compose.waitForIdle()
        repeat(15) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private companion object {
        val PICON = ArtworkId(1)
        val ART = ArtworkId(2)

        /** 1:12:30 at 0:17:25: a played cut, a named chapter, two ad breaks, an unplayed cut, chapters. */
        val MIXED = listOf(
            RecordingMark(RecordingMarkKind.CUT, 300_000L, 420_000L),
            RecordingMark(RecordingMarkKind.CHAPTER, 600_000L, name = "The long climb to the northern ridge before the first winter storm"),
            RecordingMark(RecordingMarkKind.AD_BREAK, 1_140_000L, 1_440_000L),
            RecordingMark(RecordingMarkKind.CHAPTER, 1_800_000L),
            RecordingMark(RecordingMarkKind.CUT, 2_400_000L, 2_520_000L),
            RecordingMark(RecordingMarkKind.AD_BREAK, 3_000_000L, 3_300_000L),
            RecordingMark(RecordingMarkKind.CHAPTER, 3_600_000L, name = "Night on the ridge"),
        )

        /** 4:00:00: a chapter every two minutes, an ad break every forty minutes, two cuts. */
        val DENSE = buildList {
            for (start in 120_000L until 14_400_000L step 120_000L) {
                if (start % 2_400_000L != 0L) add(RecordingMark(RecordingMarkKind.CHAPTER, start))
            }
            for (start in 2_400_000L until 14_400_000L step 2_400_000L) {
                add(RecordingMark(RecordingMarkKind.AD_BREAK, start + 660_000L, start + 900_000L))
            }
            add(RecordingMark(RecordingMarkKind.CUT, 1_000_000L, 1_060_000L))
            add(RecordingMark(RecordingMarkKind.CUT, 9_100_000L, 9_160_000L))
        }.sortedBy { it.startMs }

        /** Chapters from the file only, as a broadcast without ad detection or cuts has them. */
        val CHAPTERS = listOf(
            RecordingMark(RecordingMarkKind.CHAPTER, 480_000L, name = "Dawn"),
            RecordingMark(RecordingMarkKind.CHAPTER, 1_500_000L, name = "Above the tree line"),
            RecordingMark(RecordingMarkKind.CHAPTER, 2_640_000L, name = "Weather moves in"),
            RecordingMark(RecordingMarkKind.CHAPTER, 3_720_000L, name = "Night on the ridge"),
        )
    }
}
