package at.bernhardberger.tvhplayer.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerKeyAction
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlayerRootFocusEffectTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun autoHideTransfersFocusToRootAndTheNextUpRevealsControls() {
        var foregroundLayer by mutableStateOf(PlayerForegroundLayer.CONTROLS)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.hasWindowFocus()
        }
        composeRule.setContent {
            val rootFocus = remember { FocusRequester() }
            val controlsFocus = remember { FocusRequester() }
            PlayerRootFocusEffect(foregroundLayer, rootFocus)

            LaunchedEffect(foregroundLayer) {
                if (foregroundLayer == PlayerForegroundLayer.CONTROLS) {
                    controlsFocus.requestFocus()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ROOT)
                    .focusRequester(rootFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        val action = playerKeyAction(
                            context = PlayerKeyContext(
                                surface = PlayerSurface.LIVE,
                                controlsVisible = false,
                                seekbarFocused = false,
                                timeshiftAvailable = false,
                                simpleTvActive = false,
                            ),
                            keyCode = event.nativeKeyEvent.keyCode,
                        )
                        if (action == PlayerKeyAction.REVEAL_CONTROLS) {
                            foregroundLayer = PlayerForegroundLayer.CONTROLS
                            true
                        } else {
                            false
                        }
                    },
            ) {
                if (foregroundLayer == PlayerForegroundLayer.CONTROLS) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CONTROLS)
                            .focusRequester(controlsFocus)
                            .focusable(),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(CONTROLS).assertIsFocused()
        composeRule.runOnIdle { foregroundLayer = PlayerForegroundLayer.NONE }
        composeRule.onNodeWithTag(ROOT).assertIsFocused()

        composeRule.onNodeWithTag(ROOT).performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionUp)
        }

        composeRule.onNodeWithTag(CONTROLS).assertIsFocused()
    }
}

private const val ROOT = "live-player-focus-root"
private const val CONTROLS = "live-player-focus-controls"
