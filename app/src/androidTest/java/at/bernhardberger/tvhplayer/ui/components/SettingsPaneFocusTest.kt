package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Device checks for the C active pane, preserving the existing acceptance entry point. */
class SettingsPaneFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fourNestedLevelsRestoreParentFocusAndViewport() {
        lateinit var state: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                state = rememberDepthNavigationState("level-0")
                SettingsScreenNavigation(state, (0..3).map { depth ->
                    settingsLevel("level-$depth", "Level $depth", (0..19).map { index ->
                        settingsRow("row-$index", "$depth item $index", child = if (depth < 3) "level-${depth + 1}" else null)
                    })
                })
            }
        }
        val parents = mutableListOf<DepthFrame>()
        repeat(3) { depth ->
            repeat(12) { key(Key.DirectionDown) }
            compose.onNodeWithText("$depth item 12").assertIsFocused()
            compose.runOnIdle { parents += state.stack.active }
            key(Key.DirectionRight)
        }
        assertEquals(4, state.stack.frames.size)
        parents.asReversed().forEachIndexed { index, parent ->
            key(Key.Back)
            compose.onNodeWithText("${2 - index} item 12").assertIsFocused()
            compose.runOnIdle { assertEquals(parent, state.stack.active) }
        }
    }

    @Test fun previewIsInertAndRightOnLeafDoesNotCommit() {
        var actions = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState("root"), listOf(
                    settingsLevel("root", "Settings", listOf(
                        settingsRow("submenu", "Submenu", child = "child"),
                        settingsRow("leaf", "Leaf", onClick = { actions++ }),
                    )),
                    settingsLevel("child", "Child", listOf(settingsRow("danger", "Preview action", onClick = { actions++ }))),
                ))
            }
        }
        compose.onNodeWithText("Preview action").assertDoesNotExist()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        compose.onNodeWithText("Leaf").assertIsFocused()
        assertEquals(0, actions)
        key(Key.DirectionCenter)
        assertEquals(1, actions)
    }

    private fun key(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
}
