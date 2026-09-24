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
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
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

        // The kit's symmetric 8dp box, plus the ListItem's own 12dp padding, left the
        // label almost equidistant between the two groups. It must bind to the one below.
        val previous = compose.onNodeWithText("Ungrouped row", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val above = subheader.boundsInRoot.top - previous.bottom
        val below = title.boundsInRoot.top - subheader.boundsInRoot.bottom
        assertTrue("Subheader must sit nearer the group it heads: above=$above below=$below",
            above > below * 1.5f)

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
