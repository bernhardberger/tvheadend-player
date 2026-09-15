package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compareProductionToggleWithUnmodifiedMaterialListItem() {
        lateinit var view: View
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    Column(Modifier.width(352.dp)) {
                        settingsRow("app", "App toggle", checked = true).content(Modifier, {})
                        ListItem(selected = false, onClick = {},
                            headlineContent = { Text("Material default") },
                            trailingContent = { Switch(true, null) })
                        ListItem(selected = false, onClick = {}, modifier = Modifier.heightIn(min = 56.dp),
                            headlineContent = { Text("Material minimum56") },
                            trailingContent = { Switch(true, null) })
                        settingsRow("radio", "App radio", selected = true).content(Modifier, {})
                        settingsRow("two", "Two-line toggle", supporting = "Current value", checked = true).content(Modifier, {})
                    }
                }
            }
        }
        compose.onNodeWithText("App toggle").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/settings-c-captures").apply { mkdirs() }
        File(directory, "toggle-component-comparison.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val results = listOf("App toggle", "Material default", "Material minimum56").associateWith { label ->
            val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val ys = (text.top.toInt() - 16..text.bottom.toInt() + 16).filter { y ->
                y in 0 until 540 && (300 until 352).any { x -> bitmap.getPixel(x, y) == android.graphics.Color.rgb(121, 209, 255) }
            }
            require(ys.isNotEmpty())
            val center = (ys.first() + ys.last() + 1) / 2f
            "textCenter=${text.center.y}, switchCenter=$center, delta=${text.center.y - center}"
        }
        File(directory, "toggle-component-comparison.txt").writeText(results.entries.joinToString("\n"))
        val label = compose.onNodeWithText("App toggle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val fill = (0 until 64).filter { bitmap.getPixel(200, it) == android.graphics.Color.rgb(225, 226, 229) }
        val fillCenter = (fill.first() + fill.last() + 1) / 2f
        File(directory, "toggle-component-comparison.txt").appendText("\nFocused app containerCenter=$fillCenter, textCenter=${label.center.y}")
        assertEquals("One-line label must be centered in its focus surface", fillCenter, label.center.y, .5f)
        val supporting = compose.onNodeWithText("Current value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val headline = compose.onNodeWithText("Two-line toggle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Real supporting content retains its own line", supporting.top >= headline.bottom)
    }
}
