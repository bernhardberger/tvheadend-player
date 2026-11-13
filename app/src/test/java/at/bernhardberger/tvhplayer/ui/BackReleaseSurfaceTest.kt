package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.view.KeyEvent as NativeKey
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.components.CentredDialogShell
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel
import at.bernhardberger.tvhplayer.testutil.holdBack
import at.bernhardberger.tvhplayer.testutil.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class BackReleaseSurfaceTest {
    @get:Rule val compose = createComposeRule()

    private fun key(down: Boolean, repeat: Int = 0, flags: Int = 0) {
        compose.onRoot().performKeyPress(KeyEvent(NativeKey(0, 0,
            if (down) NativeKey.ACTION_DOWN else NativeKey.ACTION_UP,
            NativeKey.KEYCODE_BACK, repeat, 0, 0, 0, flags)))
    }

    private fun releaseCycle(unchanged: () -> Unit) {
        key(false) // The press started in another owner.
        unchanged()
        key(true)
        key(false, flags = NativeKey.FLAG_CANCELED)
        unchanged()
        holdBack(::key, beforeRelease = unchanged)
    }

    @Test fun browsePanelWaitsForItsOwnUncanceledRelease() {
        var backs = 0
        compose.setContent { TVHeadendPlayerTheme {
            Box(Modifier.fillMaxSize()) {
                BrowseSidePanel("Choices", "choices", onClose = { backs++ }) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    Button({}, Modifier.focusRequester(focus)) { Text("Choice") }
                }
            }
        } }
        releaseCycle { assertEquals(0, backs); compose.onNodeWithText("Choice").assertIsFocused() }
        assertEquals(1, backs)
        key(false)
        assertEquals(1, backs)
        key(true)
        assertEquals(1, backs)
        key(false)
        assertEquals(2, backs)
    }

    @Test fun centeredDialogWaitsForItsOwnUncanceledRelease() {
        var backs = 0
        compose.setContent { TVHeadendPlayerTheme {
            CentredDialogShell("Confirm", onDismiss = {}, onBack = { backs++ }) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Button({}, Modifier.focusRequester(focus)) { Text("Cancel") }
            }
        } }
        releaseCycle { assertEquals(0, backs); compose.onNodeWithText("Cancel").assertIsFocused() }
        assertEquals(1, backs)
        key(false)
        assertEquals(1, backs)
        key(true)
        assertEquals(1, backs)
        key(false)
        assertEquals(2, backs)
    }

    @Test fun centeredDialogShellOnlyAllowsVerticalNavigationWhenEnabled() {
        var verticalKeys by mutableStateOf(false)
        compose.setContent { TVHeadendPlayerTheme {
            CentredDialogShell("Choices", onDismiss = {}, verticalKeys = verticalKeys) {
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstFocus.requestFocus() }
                Column {
                    Button({}, Modifier.focusRequester(firstFocus)) { Text("First") }
                    Button({}) { Text("Second") }
                }
            }
        } }
        compose.onNodeWithText("First").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText("First").assertIsFocused()
        compose.runOnIdle { verticalKeys = true }
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithText("Second").assertIsFocused()
        compose.runOnIdle { verticalKeys = false }
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithText("Second").assertIsFocused()
        compose.runOnIdle { verticalKeys = true }
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithText("First").assertIsFocused()
    }

    @Test fun depthPopAndRootHandoffWaitForTheirOwnUncanceledRelease() {
        lateinit var state: DepthNavigationState
        var rootBacks = 0
        compose.setContent { TVHeadendPlayerTheme {
            state = rememberDepthNavigationState("root", "child")
            val root = DepthLevel("root", listOf(
                DepthRow(DepthItem("child", "child")) { modifier, activate ->
                    Button(activate, modifier) { Text("Child") }
                }), heading = {})
            val child = DepthLevel("child", listOf(
                DepthRow(DepthItem("leaf")) { modifier, activate ->
                    Button(activate, modifier) { Text("Leaf") }
                }), heading = {})
            DepthNavigation(state, mapOf("root" to root, "child" to child), { error(it) },
                320.dp, 24.dp, PaddingValues(24.dp), onRootBack = { rootBacks++ })
        } }
        compose.onNodeWithText("Child").assertIsFocused().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionCenter)
        }
        releaseCycle {
            assertEquals(2, state.stack.frames.size)
            compose.onNodeWithText("Leaf").assertIsFocused()
        }
        assertEquals(1, state.stack.frames.size)
        compose.onNodeWithText("Child").assertIsFocused()
        releaseCycle { assertEquals(0, rootBacks) }
        assertEquals(1, rootBacks)
        key(true)
        assertEquals(1, rootBacks)
        key(false)
        assertEquals(2, rootBacks)
    }

    @Test fun depthForgetsBackWhenFocusLeavesBeforeRelease() {
        lateinit var state: DepthNavigationState
        compose.setContent { TVHeadendPlayerTheme {
            val tabs = remember { FocusRequester() }
            val leaf = remember { FocusRequester() }
            state = rememberDepthNavigationState("root", "child")
            val root = DepthLevel("root", listOf(
                DepthRow(DepthItem("child", "child")) { modifier, activate ->
                    Button(activate, modifier) { Text("Child") }
                }), heading = {})
            val child = DepthLevel("child", listOf(
                DepthRow(DepthItem("leaf")) { modifier, activate ->
                    Button(activate, modifier.focusRequester(leaf)) { Text("Leaf") }
                }), heading = {})
            Box(Modifier.fillMaxSize()) {
                Button({}, Modifier.focusRequester(tabs).focusProperties { down = leaf }) { Text("Tabs") }
                DepthNavigation(state, mapOf("root" to root, "child" to child), { error(it) },
                    320.dp, 24.dp, PaddingValues(24.dp), onFirstRowUp = { tabs.requestFocus() })
            }
        } }
        compose.onNodeWithText("Child").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.onNodeWithText("Leaf").assertIsFocused()
        key(true)
        compose.onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionUp) }
        compose.onNodeWithText("Tabs").assertIsFocused()
        key(false, flags = NativeKey.FLAG_CANCELED)
        key(true)
        compose.onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }
        compose.onNodeWithText("Leaf").assertIsFocused()
        key(false)
        assertEquals(2, state.stack.frames.size)
        key(true)
        key(false)
        assertEquals(1, state.stack.frames.size)
    }

    @Test fun centeredDialogForgetsBackWhenDisabledMidPress() {
        var enabled by mutableStateOf(true)
        var backs = 0
        compose.setContent { TVHeadendPlayerTheme {
            CentredDialogShell("Confirm", onDismiss = {}, backEnabled = enabled, onBack = { backs++ }) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Button({}, Modifier.focusRequester(focus)) { Text("Cancel") }
            }
        } }
        key(true)
        compose.runOnIdle { enabled = false }
        key(false, flags = NativeKey.FLAG_CANCELED)
        compose.runOnIdle { enabled = true }
        key(false)
        assertEquals(0, backs)
        // Losing ownership must reset even when no release reaches the disabled site.
        key(true)
        compose.runOnIdle { enabled = false }
        compose.waitForIdle()
        compose.runOnIdle { enabled = true }
        key(false)
        assertEquals(0, backs)
        key(true)
        key(false)
        assertEquals(1, backs)
    }
}
