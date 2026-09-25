package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.ProgrammeAction
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.guide.ConfirmProgrammeActionDialog
import at.bernhardberger.tvhplayer.ui.screens.recordings.PendingRecordingAction
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingConfirmationDialog
import java.io.File
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The floating side panel on every player surface that uses it, in English and
 * German at font 1.0 and 1.3: inset 24 dp from the end, top and bottom edges at
 * its width (programme info is wider), with no row text cut. Captures go to
 * build/outputs/playback-panel-captures for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackPanelCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test fun english() = panels("en", 1f)

    @Test fun englishLargeText() = panels("en", 1.3f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = panels("de", 1f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = panels("de", 1.3f)

    /** The shared 60% scrim also sits behind the recording and guide dialogs. */
    @Test fun recordingAndGuideModals() {
        lateinit var view: View
        var guide by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                if (guide) {
                    ConfirmProgrammeActionDialog(ProgrammeAction.RECORD, PROGRAMME_TITLE, {}, {})
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
        // Over white, as in the kit's reference, a 60% black scrim reads #666666.
        assertScrim(recording.getPixel(0, 0))
        recording.recycle()
        guide = true
        compose.waitForIdle()
        println("Focus modal-guide-en-font1.0: ${focusedNodes()}")
        // The guide dialog is its own full-screen window.
        val dialog = checkNotNull(ShadowDialog.getLatestDialog()) { "No guide dialog" }
        val guideCapture = capture(checkNotNull(dialog.window).decorView, "modal-guide-en-font1.0", white = true)
        assertScrim(guideCapture.getPixel(0, 0))
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

    private fun assertScrim(corner: Int) {
        listOf(corner shr 16 and 0xFF, corner shr 8 and 0xFF, corner and 0xFF).forEach {
            assertEquals("Scrim over white", 102.0, it.toDouble(), 1.0)
        }
    }

    private enum class Surface(val panelTag: String, val width: Dp = PlaybackPanelWidth) {
        MENU("playback-options-overlay"),
        AUDIO_QUICK_LIST("playback-options-overlay"),
        SUBTITLES_QUICK_LIST("playback-options-overlay"),
        RECORDING_MENU("playback-options-overlay"),
        PROGRAMME_INFO("live-info-panel", PlaybackInfoPanelWidth),
    }

    private fun panels(locale: String, fontScale: Float) {
        lateinit var view: View
        var surface by mutableStateOf(Surface.MENU)
        val player = PlaybackOptionsTrackListTest.player(PlaybackOptionsTrackListTest.orfTracks())
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
                        Surface.AUDIO_QUICK_LIST, Surface.SUBTITLES_QUICK_LIST -> PlaybackOptionsSheet(
                            if (surface == Surface.AUDIO_QUICK_LIST) {
                                PlaybackOptionsPage.AUDIO
                            } else {
                                PlaybackOptionsPage.SUBTITLES
                            },
                            player, false, AspectRatioMode.FIT, false,
                            {}, {}, {}, audioAutomatic = true, onAutomaticAudio = {},
                            quickList = PlaybackQuickListSignals(),
                        )
                        Surface.PROGRAMME_INFO -> LiveProgrammeInfoOverlay(
                            event = EpgEvent.create(
                                id = EventId(42L),
                                channelId = ChannelId(1L),
                                start = Instant.fromEpochSeconds(1_000L),
                                stop = Instant.fromEpochSeconds(4_600L),
                                title = PROGRAMME_TITLE,
                                summary = PROGRAMME_SUMMARY,
                                description = PROGRAMME_SUMMARY,
                            ),
                            channelIdentity = "1 • ORF1 HD",
                            channelName = "ORF1 HD",
                            recordingScheduled = false,
                            canRecord = true,
                            recordingState = LiveInfoRecordingState.Idle,
                            confirmationVisible = false,
                            restoreRecordFocus = false,
                            onRecord = {},
                            onRecordingActivate = {},
                            onRecordingDismiss = {},
                            onClose = {},
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
        // Programme info opens on its reading region; for review, also capture each
        // action focused.
        listOf("live-info-record", "live-info-close").forEach { tag ->
            println("Bounds $locale-font$fontScale $tag: ${compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()}")
        }
        listOf("live-info-record", "live-info-close").forEach { tag ->
            compose.onNodeWithTag(tag).requestFocus()
            compose.onNodeWithTag(tag).assertIsFocused()
            val name = "programme_info-$locale-font$fontScale-${tag.removePrefix("live-info-")}-focused"
            println("Focus $name: ${focusedNodes()}")
            capture(view, name).recycle()
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
    private fun capture(view: View, name: String, white: Boolean = false): Bitmap = compose.runOnIdle {
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (white) canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        val directory = File("build/outputs/playback-panel-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap
    }

    private companion object {
        const val PROGRAMME_TITLE = "Die Rosenheim-Cops: Eine außergewöhnlich lange Sendungsbezeichnung"
        const val PROGRAMME_SUMMARY = "Ein Bauunternehmer wird tot in seiner Baugrube gefunden. " +
            "Hofer und Stadler ermitteln zwischen Bauamt, Nachbarschaftsstreit und einer alten Rechnung, " +
            "die nie bezahlt wurde."
    }
}
