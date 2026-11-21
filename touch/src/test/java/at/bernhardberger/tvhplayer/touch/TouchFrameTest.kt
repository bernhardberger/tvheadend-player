package at.bernhardberger.tvhplayer.touch

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchFrameTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val destination = hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun expandedFrame() {
        assertChannelsFrame(vertical = true)
        capture("tablet-landscape", 2560, 1600, statusBarDp = 32)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun mediumFrame() {
        assertChannelsFrame(vertical = false)
        capture("tablet-upright", 1600, 2560, statusBarDp = 32)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun compactFrameNavigatesAndRestoresSelection() {
        assertChannelsFrame(vertical = false)
        capture("phone", 824, 1784, statusBarDp = 40)

        compose.onNode(destination and hasText("Guide")).performClick()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNode(destination and hasText("Guide")).assertIsSelected()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    private fun assertChannelsFrame(vertical: Boolean) {
        val labels = compose.onAllNodes(destination).fetchSemanticsNodes()
            .sortedBy { if (vertical) it.boundsInRoot.top else it.boundsInRoot.left }
            .map { it.config.getOrNull(SemanticsProperties.Text)?.single()?.text }
        assertEquals(listOf("Channels", "Guide", "Recordings"), labels)
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    private fun capture(name: String, width: Int, height: Int, statusBarDp: Int) {
        // Robolectric doesn't supply system-bar insets. Reserve the space shown in the drawings.
        compose.runOnIdle {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val density = content.resources.displayMetrics.density
            val top = (statusBarDp * density).roundToInt()
            val bottom = (24 * density).roundToInt()
            ViewCompat.dispatchApplyWindowInsets(
                content,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                    .build(),
            )
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(width to height, bitmap.width to bitmap.height)
        val directory = File(checkNotNull(System.getProperty("touch.slice0.captureDir")))
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
