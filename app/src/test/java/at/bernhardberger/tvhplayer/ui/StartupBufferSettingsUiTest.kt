package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Start-up buffer choice level under Settings > Player, in English and
 * German at font 1.0 and 1.3. Captures go to build/outputs/startup-buffer-captures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class StartupBufferSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test fun english() = level("en", 1f)
    @Test fun englishLargeText() = level("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun german() = level("de", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun germanLargeText() = level("de", 1.3f)

    private fun level(locale: String, scale: Float) {
        val german = locale == "de"
        val title = if (german) "Startpuffer" else "Start-up buffer"
        // Automatic starts at 0.5 s, shown in locale decimals.
        val automatic = if (german) "Automatisch · 0,5 s" else "Automatic · 0.5 s"
        val half = if (german) "0,5 s" else "0.5 s"
        val oneAndHalf = if (german) "1,5 s" else "1.5 s"
        val description = if (german) "Kürzer startet schneller, länger schützt vor Aussetzern."
            else "Shorter starts faster; longer protects against dropouts."
        var ui by mutableStateOf(SettingsPlayerUiState())
        var selections = 0
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    val navigation = rememberDepthNavigationState("root")
                    val levels = listOf(
                        settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                        settingsPlayerLevel(ui, {}, {}, {}, {}),
                        settingsStartupBufferLevel(ui.startupBufferMillis, ui.startupBufferLearnedMillis) {
                            selections++
                            ui = ui.copy(startupBufferMillis = it)
                            navigation.pop()
                        },
                    )
                    Box(Modifier.fillMaxSize()) { SettingsScreenNavigation(navigation, levels) }
                }
            }
        }
        key(Key.DirectionCenter)
        // The row sits right after "Keep channel", so Down reaches it from the first player row.
        var downs = 0
        while (focusedRows(title).isEmpty()) {
            check(++downs < 20) { "Start-up buffer row not reachable" }
            key(Key.DirectionDown)
        }
        key(Key.DirectionUp)
        val keepChannel = if (german) "Sender nach Verlassen der App beibehalten" else "Keep channel after leaving the app"
        compose.onNode(isFocused() and hasText(keepChannel)).assertExists()
        // The long title wraps to a second line instead of being cut off.
        assertFalse("Keep channel title is cut off", overflows(keepChannel))
        assertFalse("Start-up buffer title is cut off", overflows(title))
        key(Key.DirectionDown)
        compose.onNode(isFocused() and hasText(title) and hasText(automatic)).assertExists()
        capture(view, "$locale-player-font$scale")

        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText(automatic)).assertIsSelected()
        // The explanation belongs to the level: it sits under the heading, outside
        // every row, not focusable, and no option carries it.
        compose.onNodeWithText(description).assertIsDisplayed()
        compose.onNode(isFocused() and hasText(description)).assertDoesNotExist()
        compose.onNode(hasText(description) and hasClickAction()).assertDoesNotExist()
        val descriptionBounds = compose.onNodeWithText(description, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val rows = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        check(rows.isNotEmpty())
        for (row in rows) {
            assertFalse("Description overlaps a row", row.boundsInRoot.overlaps(descriptionBounds))
        }
        assertFalse("Description is cut off", overflows(description))
        for (label in listOf(half, "1 s", oneAndHalf, "2 s", "3 s")) {
            compose.onNode(hasText(label) and hasClickAction()).assertIsNotSelected()
        }
        capture(view, "$locale-level-font$scale")

        // Back leaves the level without choosing.
        key(Key.Back)
        compose.onNode(isFocused() and hasText(title)).assertExists()
        assertEquals(0, selections)

        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(1, selections)
        assertEquals(500, ui.startupBufferMillis)
        compose.onNode(isFocused() and hasText(title) and hasText(half)).assertExists()

        // Reopening starts on the stored choice; Automatic shows the learned level.
        key(Key.DirectionCenter)
        compose.onNode(isFocused() and hasText(half)).assertIsSelected()
        capture(view, "$locale-level-fixed-font$scale")
        compose.runOnIdle { ui = ui.copy(startupBufferLearnedMillis = 1500) }
        key(Key.DirectionUp)
        val learned = if (german) "Automatisch · 1,5 s" else "Automatic · 1.5 s"
        compose.onNode(isFocused() and hasText(learned)).assertIsNotSelected()
        key(Key.DirectionCenter)
        assertEquals(STARTUP_BUFFER_AUTOMATIC, ui.startupBufferMillis)
        compose.onNode(isFocused() and hasText(title) and hasText(learned)).assertExists()
    }

    private fun overflows(text: String): Boolean {
        val nodes = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.isNotEmpty())
        return nodes.any { node ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            layouts.single().hasVisualOverflow
        }
    }

    private fun focusedRows(title: String) =
        compose.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes()

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun capture(view: View, name: String) = compose.runOnIdle {
        assertEquals(1920, view.width)
        assertEquals(1080, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/outputs/startup-buffer-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
