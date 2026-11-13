package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.testutil.labelToFocusedFillGap
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveDateDivider
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import java.io.File
import org.junit.experimental.categories.Category
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings groups are separated by a subheader taking the design kit's "List / Subheader"
 * type — a 12sp medium uppercase label at the list item's own 16dp content inset — but not
 * the kit's colour or spacing. It previously reused titleMedium, the row *title* style, so
 * a group heading rendered identically to the rows it was meant to separate. The kit board
 * is authored standalone and carries a flat neutral with a symmetric 8dp box, which in a
 * real list leaves the label as bright as the titles and floating between both groups.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSectionSubheaderTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var view: View

    /**
     * A focused row grows towards the label above it. A Settings label keeps the first Recordings
     * date label's air above a focused row: that label stands in the same list as the reference.
     */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    fun aFocusedRowKeepsTheFirstDateLabelsAirUnderASettingsLabel() {
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Column(Modifier.padding(16.dp).width(340.dp).background(Color.Black)) {
                    ArchiveDateDivider("Today", first = true)
                    settingsRow("reference", "Reference row").content(Modifier, {})
                    settingsRow("grouped", "Grouped row", section = "Navigation").content(Modifier, {})
                }
            }
        }
        fun gapAbove(row: String, label: String): Int {
            compose.onNodeWithTag("settings-row-$row").requestFocus()
            compose.waitForIdle()
            val node = compose.onAllNodes(hasText(label, ignoreCase = true), useUnmergedTree = true)
                .fetchSemanticsNodes().first()
            return compose.labelToFocusedFillGap(view, node.boundsInRoot)
        }
        val reference = gapAbove("reference", "Today")
        val settings = gapAbove("grouped", "Navigation")
        assertTrue("A Settings label sits $settings px above a focused row, the first date label $reference px",
            settings >= reference)
    }

    /** The Playback page with a focused row under its first label and under a later one, for review. */
    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    fun settingsLabelsAboveAFocusedRowCaptures() {
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.PLAYER.name)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SideRail(currentRoute = AppDestination.SETTINGS, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                        SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                            settingsPlayerLevel(SettingsPlayerUiState(), {})),
                            initialFocusEnabled = !drawerActive, contentPadding = padding)
                    }
                }
            }
        }
        compose.pressKey(Key.DirectionCenter, settleFrames = 12)
        fun focusUnder(label: String, name: String) {
            repeat(16) {
                val heading = compose.onAllNodes(hasText(label, ignoreCase = true), useUnmergedTree = true)
                    .fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                val row = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
                if (heading != null && row != null && heading.bottom <= row.top + 1f && row.top - heading.bottom < 48f) {
                    compose.mainClock.advanceTimeBy(1_000)
                    val settled = compose.onAllNodes(hasText(label, ignoreCase = true), useUnmergedTree = true)
                        .fetchSemanticsNodes().first().boundsInRoot
                    val gap = compose.labelToFocusedFillGap(view, settled)
                    capture(name, "label ink to focused fill=$gap px; label=$settled")
                    return
                }
                compose.pressKey(Key.DirectionDown, settleFrames = 6)
            }
            fail("No focused row under $label")
        }
        focusUnder("Recording marks", "settings-first-label-focused-row")
        // Streaming's row is disabled without a server, outlined instead of filled; Picture's is not.
        focusUnder("Picture", "settings-later-label-focused-row")
    }

    private fun capture(name: String, report: String) {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        CaptureOutput.writePng(File("build/outputs/list-label-spacing/$name.png"), bitmap)
        CaptureOutput.writeText(File("build/outputs/list-label-spacing/$name.txt")) {
            "Production SettingsScreenNavigation + SideRail, Playback page; 960x540dp xhdpi; en; fontScale=1\n$report\n"
        }
        bitmap.recycle()
    }

    @Test fun sectionSubheaderIsDistinctFromRowTitlesAndAlignsWithThem() {
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                // Peak-ink comparison below needs a known ground, not the default window.
                Column(Modifier.width(352.dp).background(Color.Black)) {
                    settingsRow("plain", "Ungrouped row").content(Modifier, {})
                    settingsRow("grouped", "Grouped row", section = "Navigation").content(Modifier, {})
                }
            }
        }
        compose.waitForIdle()

        val subheader = compose.onNodeWithContentDescription("Navigation", useUnmergedTree = true)
            .fetchSemanticsNode()
        val title = compose.onNodeWithText("Grouped row", useUnmergedTree = true).fetchSemanticsNode()

        assertEquals("Subheader renders the kit's uppercase label",
            listOf("NAVIGATION"), subheader.config[SemanticsProperties.Text].map { it.text })
        assertTrue("Subheader must not reuse the row title type scale, " +
            "subheader=${subheader.boundsInRoot} title=${title.boundsInRoot}",
            subheader.boundsInRoot.height < title.boundsInRoot.height)
        assertEquals("Subheader sits at the list item's own content inset",
            title.boundsInRoot.left, subheader.boundsInRoot.left, .5f)
        assertTrue("Subheader introduces its group, so it precedes the row",
            subheader.boundsInRoot.bottom <= title.boundsInRoot.top)

        // The label binds to the group below it: between the rows, no more air under it than over
        // it, though a focused row needs some (see the focused-row rule above).
        val previous = compose.onNodeWithTag("settings-row-plain", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("settings-row-grouped", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val above = subheader.boundsInRoot.top - previous.bottom
        val below = next.top - subheader.boundsInRoot.bottom
        assertTrue("Subheader must sit nearer the group it heads: above=$above below=$below",
            below > 0f && above >= below)

        // It must also recede from the row titles rather than match them, which a colour
        // equal to onSurface would not do. Compare peak ink, not a hex value.
        lateinit var pixels: Bitmap
        compose.runOnUiThread {
            pixels = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(pixels))
        }
        fun peak(bounds: androidx.compose.ui.geometry.Rect): Float {
            var best = 0f
            for (y in bounds.top.toInt() until bounds.bottom.toInt().coerceAtMost(pixels.height)) {
                for (x in bounds.left.toInt() until bounds.right.toInt().coerceAtMost(pixels.width)) {
                    val p = Color(pixels.getPixel(x, y))
                    best = maxOf(best, maxOf(p.red, p.green, p.blue))
                }
            }
            return best
        }
        val subheaderInk = peak(subheader.boundsInRoot)
        val titleInk = peak(title.boundsInRoot)
        assertTrue("Subheader must be dimmer than the row titles it separates, " +
            "subheader=$subheaderInk title=$titleInk", subheaderInk < titleInk)
    }
}
