package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsPlayerLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class SettingsAudioPassthroughTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteToggleStaysFocusedAndDoesNotCommitFromFocus() = exercise("Audio passthrough", 1f)

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeTextKeepsTheSwitchReachable() = exercise("Audio-Passthrough", 1.3f)

    private fun exercise(label: String, fontScale: Float) {
        var enabled by mutableStateOf(true)
        var failed by mutableStateOf(false)
        var changes = 0
        lateinit var view: View
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize()) {
                        SettingsScreenNavigation(rememberDepthNavigationState("root"), listOf(
                            settingsLevel("root", "Settings", listOf(settingsRow("player", "Open player", child = SettingsSection.PLAYER.name))),
                            settingsPlayerLevel(SettingsPlayerUiState(audioPassthroughEnabled = enabled,
                                audioPassthroughChangeFailed = failed), {}, {}, {}, {
                                changes++
                                enabled = it
                            }),
                        ))
                    }
                }
            }
        }
        fun key(key: Key) {
            compose.onRoot().performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Open player").assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        compose.onNodeWithText(label).assertIsDisplayed().assertIsFocused().assertIsOn()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/settings-audio-captures").apply { mkdirs() }
            File(directory, "passthrough-font$fontScale.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        assertEquals(0, changes)
        key(Key.DirectionCenter)
        compose.onNodeWithText(label).assertIsFocused().assertIsOff()
        assertEquals(1, changes)
        key(Key.DirectionCenter)
        compose.onNodeWithText(label).assertIsFocused().assertIsOn()
        assertEquals(2, changes)
        compose.runOnIdle { failed = true }
        key(Key.DirectionCenter)
        compose.onNodeWithText(label).assertIsFocused().assertIsOff()
        assertEquals(3, changes) // Failure must not trap the user in the unapplied desired mode.
        key(Key.DirectionCenter)
        compose.onNodeWithText(label).assertIsFocused().assertIsOn()
        assertEquals(4, changes) // The desired mode can be tried again normally.
        key(Key.Back)
        compose.onNodeWithText("Open player").assertIsFocused()
        assertEquals(4, changes)
    }
}
