package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsRow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Retains the device navigation acceptance entry point for the recursive Settings owner. */
class SettingsStartupEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startupDefersAutomaticFocusUntilContentIsAdmitted() {
        var enabled by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState("root"),
                    listOf(settingsLevel("root", "Settings", listOf(settingsRow("general", "General")))),
                    initialFocusEnabled = enabled)
            }
        }
        compose.onNodeWithText("General").assertIsNotFocused()
        compose.runOnIdle { enabled = true }
        compose.onNodeWithText("General").assertIsFocused()
    }

    @Test fun enteringKeyReleaseCannotActivateReplacementLeaf() {
        var actions = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState("root"), listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("general", "General", child = "general"))),
                    settingsLevel("general", "General", listOf(settingsRow("action", "Action", onClick = { actions++ }))),
                ))
            }
        }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.onNodeWithText("General").assertIsFocused() // press alone never enters
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithText("Action").assertIsFocused()
        assertEquals(0, actions)
        key(Key.DirectionCenter)
        assertEquals(1, actions)
    }

    @Test fun localBackRestoresInvokingItemBeforeRootBack() {
        lateinit var state: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                state = rememberDepthNavigationState("root", "second")
                SettingsScreenNavigation(state, listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("first", "First"), settingsRow("second", "Second", child = "child"))),
                    settingsLevel("child", "Child", listOf(settingsRow("action", "Action"))),
                ))
            }
        }
        key(Key.DirectionRight)
        key(Key.Back)
        compose.onNodeWithText("Second").assertIsFocused()
        assertEquals(1, state.stack.frames.size)
    }

    @Test fun localeLikeRecreationKeepsPoppedParentAndUpdatedValue() {
        val restoration = StateRestorationTester(compose)
        lateinit var state: DepthNavigationState
        restoration.setContent {
            TVHeadendPlayerTheme {
                var value by rememberSaveable { mutableStateOf("Follow system") }
                state = rememberDepthNavigationState("general")
                SettingsScreenNavigation(state, listOf(
                    settingsLevel("general", "General", listOf(settingsRow("language", "App language", value, child = "languages"))),
                    settingsLevel("languages", "Language", listOf(settingsRow("de", "German", onClick = { state.pop(); value = "German" }))),
                ))
            }
        }
        key(Key.DirectionCenter)
        key(Key.DirectionCenter)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("App language").assertIsFocused()
        compose.onNodeWithText("German").assertExists()
        assertEquals("general", state.stack.active.levelId)
    }

    @Test fun dedicatedEditorIsNotMountedByPreviewAndIsRemovedOnDeparture() {
        var mounted = 0
        var current by mutableStateOf(true)
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState("root"), listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("connection", "Connection", child = "editor"))),
                    settingsLevel("editor", "Connection", listOf(settingsRow("safe", "Open connection settings")),
                        activeContent = { requester, _ ->
                            DisposableEffect(Unit) { mounted++; onDispose { mounted-- } }
                            Button(onClick = {}, modifier = Modifier.focusRequester(requester)) { Text("Editor") }
                        }),
                ), isCurrent = current)
            }
        }
        assertEquals(0, mounted)
        key(Key.DirectionCenter)
        compose.waitForIdle()
        assertEquals(1, mounted)
        compose.runOnIdle { current = false }
        compose.waitForIdle()
        assertEquals(0, mounted)
    }

    @Test fun missingActiveItemFallsBackToNearestRemainingRow() {
        var rows by mutableStateOf(listOf("A", "B", "C"))
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState("root", "B"),
                    listOf(settingsLevel("root", "Settings", rows.map { settingsRow(it, it) })))
            }
        }
        compose.onNodeWithText("B").assertIsFocused()
        compose.runOnIdle { rows = listOf("A", "C") }
        compose.onNodeWithText("C").assertIsFocused()
    }

    private fun key(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
}
