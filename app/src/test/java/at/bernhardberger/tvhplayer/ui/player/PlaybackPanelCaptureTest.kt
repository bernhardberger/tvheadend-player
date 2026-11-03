package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.core.ProgrammeCategory
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.components.ListBottomFadeVisible
import at.bernhardberger.tvhplayer.ui.components.ListTopFadeVisible
import at.bernhardberger.tvhplayer.ui.components.PanelListBottomFade
import at.bernhardberger.tvhplayer.ui.components.PanelListTopFade
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideFilterPanel
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideTimePanel
import java.time.Instant
import java.time.ZoneId
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The floating side panel on every player surface that uses it, in English and
 * German at font 1.0 and 1.3: inset 24 dp from the end, top and bottom edges at
 * its width, with no row text cut. Live programme info is the full-screen player
 * page now and is captured by PlayerChromeCaptureTest. Captures go to
 * build/outputs/playback-panel-captures for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(VisualCapture::class)
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
class PlaybackPanelCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test fun english() = panels("en", 1f)

    @OptIn(ExperimentalTestApi::class)
    @Test fun browseListFadesAndFocus() {
        val view = panelScene {
            GuideFilterPanel(ProgrammeCategory.entries.first(), true, {}, {})
        }
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(ListBottomFadeVisible))
        assertTrue(list.fetchSemanticsNode().config[ListBottomFadeVisible])
        assertFalse(list.fetchSemanticsNode().config[ListTopFadeVisible])
        capture(view, "guide-filter-top-en-font1.0", output = "043-panels").recycle()
        ProgrammeCategory.entries.forEachIndexed { index, category ->
            if (index > 0) compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            val row = compose.onNodeWithTag("guide-genre-${category.name.lowercase()}")
            row.assertIsFocused()
            val bounds = list.getUnclippedBoundsInRoot()
            val rowBounds = row.getUnclippedBoundsInRoot()
            val semantics = list.fetchSemanticsNode().config
            if (semantics[ListBottomFadeVisible]) assertTrue("Focus above bottom fade", rowBounds.bottom <= bounds.bottom - PanelListBottomFade)
            if (semantics[ListTopFadeVisible]) assertTrue("Focus below top fade", rowBounds.top >= bounds.top + PanelListTopFade)
        }
        assertFalse(list.fetchSemanticsNode().config[ListBottomFadeVisible])
        assertTrue(list.fetchSemanticsNode().config[ListTopFadeVisible])
        val panel = compose.onNodeWithTag("guide-genre-panel").getUnclippedBoundsInRoot()
        assertEquals("Long list reaches panel edge", panel.bottom.value, list.getUnclippedBoundsInRoot().bottom.value, 0.5f)
        capture(view, "guide-filter-bottom-en-font1.0", output = "043-panels").recycle()
    }

    @Test fun fittingBrowseListHasNoFadesAndDoesNotStretch() {
        panelScene {
            BrowseSidePanel("View options", "short-panel", {}) {
                BrowsePanelList(listOf(BrowsePanelEntry.Choice("short-row", "Channel names", true, {})), radio = false)
            }
        }
        compose.onNodeWithTag("short-row").assertIsFocused()
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(ListBottomFadeVisible))
        assertFalse(list.fetchSemanticsNode().config[ListBottomFadeVisible])
        assertFalse(list.fetchSemanticsNode().config[ListTopFadeVisible])
        val panel = compose.onNodeWithTag("short-panel").getUnclippedBoundsInRoot()
        assertTrue("Short list does not stretch", list.getUnclippedBoundsInRoot().bottom < panel.bottom)
    }

    /** A controlled scroll pose for reviewing a non-focusable heading under the title. */
    @Test fun browseSectionHeadingAtTopFade() {
        val entries = buildList {
            repeat(12) { index ->
                if (index == 4) add(BrowsePanelEntry.Section("Channel groups"))
                add(BrowsePanelEntry.Choice("group-$index", "Channel group ${index + 1}", index == 5, {}))
            }
        }
        val view = panelScene {
            BrowseSidePanel("Tags", "section-panel", {}) { BrowsePanelList(entries, radio = false) }
        }
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(ListBottomFadeVisible))
        list.performScrollToIndex(4).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
        compose.waitForIdle()
        assertTrue(list.fetchSemanticsNode().config[ListTopFadeVisible])
        compose.onNodeWithTag("group-5").assertIsFocused()
        capture(view, "section-under-title-en-font1.0", output = "043-panels").recycle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun guideTimeTwoLists() {
        val now = Instant.parse("2026-11-03T12:00:00Z").epochSecond
        val view = panelScene {
            GuideTimePanel(now, now + 7200, GuideWindowBounds(now - 21600, now + 7 * 86400), ZoneId.of("UTC"), { now }, {}, {}, {})
        }
        compose.onNodeWithTag("guide-time-day-0").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithTag("guide-time-preset-12").assertIsFocused()
        val lists = compose.onAllNodes(SemanticsMatcher.keyIsDefined(ListBottomFadeVisible)).fetchSemanticsNodes()
        assertEquals(2, lists.size)
        assertTrue("Both panes have width", lists.all { it.boundsInRoot.width > 300f })
        assertTrue("Day list scrolls", lists.first().config[ListBottomFadeVisible])
        compose.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        compose.onNodeWithTag("guide-time-preset-22").assertIsFocused()
        capture(view, "guide-time-en-font1.0", output = "043-panels").recycle()
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("guide-time-day-0").assertIsFocused()
        repeat(7) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("guide-time-day-7").assertIsFocused()
        val dayList = compose.onAllNodes(SemanticsMatcher.keyIsDefined(ListBottomFadeVisible)).fetchSemanticsNodes().first()
        assertFalse("Day list reaches its end", dayList.config[ListBottomFadeVisible])
        assertTrue("Day list can scroll back", dayList.config[ListTopFadeVisible])
    }

    @Test fun playerListUnchanged() {
        val view = panelScene {
            PlaybackOptionsSheet(PlaybackOptionsPage.AUDIO,
                PlaybackOptionsTrackListTest.player(PlaybackOptionsTrackListTest.orfTracks()),
                false, AspectRatioMode.FIT, false, {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {})
        }
        compose.onNodeWithTag("playback-options-track-automatic").assertIsFocused()
        assertTrue(compose.onNodeWithTag("playback-options-track-list").fetchSemanticsNode().config[ListBottomFadeVisible])
        capture(view, "player-audio-en-font1.0", output = "043-panels").recycle()
    }

    /** Match the browse shell, early-morning bounds and 12-hour clock of the integrated report. */
    @Test fun guideTimeCurrentDayKeepsFirstRowBelowTitle() {
        val previousClock = AppClock.systemUses24Hour
        AppClock.systemUses24Hour = false
        try {
            val now = Instant.parse("2026-11-03T01:32:00Z").epochSecond
            val view = panelScene {
                SideRail(currentRoute = AppDestination.GUIDE, onRootBack = {}, onNavigate = {}) { _, _ ->
                    GuideTimePanel(now, now + 7200, GuideWindowBounds(now - 21600, now + 7 * 86400),
                        ZoneId.of("UTC"), { now }, {}, {}, {})
                }
            }
            compose.onNodeWithTag("guide-time-day-0").assertIsFocused()
            capture(view, "guide-time-current-day-en-font1.0", output = "043-panels").recycle()
            val first = compose.onNodeWithTag("guide-time-now")
            val full = first.getUnclippedBoundsInRoot()
            val clipped = first.fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithText("Day and time").getUnclippedBoundsInRoot()
            assertTrue("Now begins below the title: $full / $title", full.top >= title.bottom)
            assertEquals("Now is fully visible, not clipped by the list", (full.bottom - full.top).value * 2f, clipped.height, 0.5f)
            val lists = compose.onAllNodes(SemanticsMatcher.keyIsDefined(ListTopFadeVisible)).fetchSemanticsNodes()
            assertFalse("Opening near the top must not scroll the day list", lists.first().config[ListTopFadeVisible])
        } finally {
            AppClock.systemUses24Hour = previousClock
        }
    }

    private fun panelScene(content: @Composable () -> Unit): View {
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val still = remember { FixtureArt.art("still-ridge-light").asImageBitmap() }
                    Box(Modifier.fillMaxSize()) {
                        Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
        return view
    }

    @Test fun englishLargeText() = panels("en", 1.3f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = panels("de", 1f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = panels("de", 1.3f)

    /** Centred confirmations dim the backdrop once and keep the safe pill visibly focused. */
    @Test fun recordingAndGuideModals() {
        lateinit var view: View
        var guide by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                if (guide) {
                    ConfirmProgrammeActionDialog(ProgrammeAction.STOP_RECORDING, PROGRAMME_TITLE, {}, {})
                } else {
                    RecordingConfirmationDialog(PendingRecordingAction.DELETE, PROGRAMME_TITLE, true, {}, {})
                }
            }
        }
        // At first idle, with no key pressed, Back has focus and draws it: the tv
        // focused fill, not the unfocused outline alone.
        assertEquals(listOf("recording-confirmation-back"), focusedNodes())
        val recording = capture(view, "modal-recording-en-font1.0", white = true)
        val back = compose.onNodeWithTag("recording-confirmation-back").getUnclippedBoundsInRoot()
        val fill = lightFillShare(recording, back.left.value, back.top.value, back.right.value, back.bottom.value)
        assertTrue("Back draws the focused fill, share $fill", fill > 0.3f)
        assertEquals("Single 60% recording scrim", android.graphics.Color.rgb(102, 102, 102), recording.getPixel(0, 0))
        recording.recycle()
        guide = true
        compose.waitForIdle()
        println("Focus modal-guide-en-font1.0: ${focusedNodes()}")
        val guideCapture = capture(view, "modal-guide-en-font1.0", white = true)
        assertEquals("Single 60% guide scrim", android.graphics.Color.rgb(102, 102, 102), guideCapture.getPixel(0, 0))
        guideCapture.recycle()
    }

    /** Share of pixels inside a dp rect (2 px per dp) in the tv focused button fill, #E1E2E5. */
    private fun lightFillShare(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Float {
        val pixels = (left * 2).toInt() until (right * 2).toInt()
        val rows = (top * 2).toInt() until (bottom * 2).toInt()
        val light = rows.sumOf { y ->
            pixels.count { x ->
                val c = bitmap.getPixel(x, y)
                listOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF).zip(listOf(225, 226, 229))
                    .all { (value, target) -> kotlin.math.abs(value - target) <= 8 }
            }
        }
        return light.toFloat() / (pixels.count() * rows.count())
    }

    private enum class Surface(val panelTag: String, val width: Dp = PlaybackPanelWidth) {
        MENU("playback-options-overlay"),
        AUDIO_QUICK_LIST("playback-options-overlay"),
        SUBTITLES_QUICK_LIST("playback-options-overlay"),
        // As the Subtitles button opens it on a programme with subtitles.
        SUBTITLES_QUICK_LIST_TRACKS("playback-options-overlay"),
        RECORDING_MENU("playback-options-overlay"),
    }

    private fun panels(locale: String, fontScale: Float) {
        lateinit var view: View
        var surface by mutableStateOf(Surface.MENU)
        val player = PlaybackOptionsTrackListTest.player(PlaybackOptionsTrackListTest.orfTracks())
        val withSubtitles = PlaybackOptionsTrackListTest.player(Tracks(PlaybackOptionsTrackListTest.orfTracks().groups +
            listOf("de", "en").map { language ->
                Tracks.Group(TrackGroup("dvb-$language", Format.Builder().setId("dvb-$language")
                    .setSampleMimeType(MimeTypes.APPLICATION_DVBSUBS).setLanguage(language).build()),
                    false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
            }))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    when (surface) {
                        Surface.MENU, Surface.RECORDING_MENU -> PlaybackOptionsSheet(
                            PlaybackOptionsPage.ROOT, player, false, AspectRatioMode.FIT, false,
                            {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {},
                            recording = surface == Surface.RECORDING_MENU,
                        )
                        Surface.AUDIO_QUICK_LIST, Surface.SUBTITLES_QUICK_LIST, Surface.SUBTITLES_QUICK_LIST_TRACKS -> PlaybackOptionsSheet(
                            if (surface == Surface.AUDIO_QUICK_LIST) {
                                PlaybackOptionsPage.AUDIO
                            } else {
                                PlaybackOptionsPage.SUBTITLES
                            },
                            if (surface == Surface.SUBTITLES_QUICK_LIST_TRACKS) withSubtitles else player,
                            false, AspectRatioMode.FIT, false,
                            {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {},
                            quickList = PlaybackQuickListSignals(),
                        )
                    }
                }
            }
        }
        val cut = Surface.entries.flatMap { next ->
            surface = next
            compose.waitForIdle()
            assertPanelInset(next.panelTag, next.width)
            val name = "${next.name.lowercase()}-$locale-font$fontScale"
            println("Focus $name: ${focusedNodes()}")
            capture(view, name).recycle()
            cutTexts(next.panelTag).map { "$next: $it" }
        }
        assertEquals("Cut text", emptyList<String>(), cut)
    }

    /** Test tag, or else merged text, of every focused node in any window. */
    private fun focusedNodes(): List<String> =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
                ?: compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull { it.id == node.id }
                    ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                ?: "node ${node.id}"
        }

    private fun assertPanelInset(tag: String, width: Dp) {
        val panel = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertEquals("$tag width", width.value, (panel.right - panel.left).value, 0.5f)
        assertEquals("$tag end inset", 24f, 960f - panel.right.value, 0.5f)
        assertEquals("$tag top inset", 24f, panel.top.value, 0.5f)
        assertEquals("$tag bottom inset", 24f, 540f - panel.bottom.value, 0.5f)
        assertEquals(24.dp, PlaybackPanelEdgeInset)
    }

    /** Every text in the panel whose last line is ellipsized. */
    private fun cutTexts(tag: String): List<String> {
        fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap { listOf(it) + it.descendants() }
        return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().descendants()
            .filter { SemanticsActions.GetTextLayoutResult in it.config }
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
                val layout = results.single()
                layout.layoutInput.text.text.takeIf { layout.isLineEllipsized(layout.lineCount - 1) }
            }
    }

    /** Saves a capture of [view] and returns it; the caller recycles it. */
    private fun capture(view: View, name: String, white: Boolean = false, output: String = "playback-panel-captures"): Bitmap = compose.runOnIdle {
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (white) canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        val directory = File("build/outputs/$output").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap
    }

    private companion object {
        const val PROGRAMME_TITLE = "Die Rosenheim-Cops: Eine außergewöhnlich lange Sendungsbezeichnung"
    }
}
