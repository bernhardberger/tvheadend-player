package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ApplianceLaunchRecoveryHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passiveStatusHidesAndBlocksTheUnderlyingContentPlane() {
        var activations = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ApplianceLaunchRecoveryHost(
                    visible = true,
                    actionable = false,
                    onBack = {},
                    overlay = {
                        TvRecoveryOverlay(
                            visible = true,
                            message = "Starting television",
                        )
                    },
                ) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(focus) { focus.requestFocus() }
                    Button(
                        onClick = { activations++ },
                        modifier = Modifier.focusRequester(focus),
                    ) {
                        Text("Underlying action")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Underlying action").assertDoesNotExist()
        composeRule.onRoot().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(0, activations) }
    }

    @Test
    fun actionableRecoveryKeepsItsButtonReachableAndOwnsBackOnce() {
        var retries = 0
        var backs = 0
        var escapedBackKeyUps = 0
        var visible by mutableStateOf(true)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ApplianceLaunchRecoveryHost(
                    visible = visible,
                    actionable = true,
                    onBack = {
                        backs++
                        visible = false
                    },
                    overlay = {
                        TvRecoveryOverlay(
                            visible = visible,
                            message = "Connection interrupted",
                            primaryActionLabel = "Retry",
                            onPrimaryAction = { retries++ },
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                                escapedBackKeyUps++
                            }
                            false
                        }
                    ) {
                        Button(onClick = {}) { Text("Underlying action") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Retry")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onRoot().performKeyInput { pressKey(Key.Back) }
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, backs)
            assertEquals(0, escapedBackKeyUps)
        }
    }
}
