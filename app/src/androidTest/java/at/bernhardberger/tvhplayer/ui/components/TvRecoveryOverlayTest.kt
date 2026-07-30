package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvRecoveryOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retryAndCloseFocusPrimaryAndContainEveryDirectionalEdge() {
        var retries = 0
        var closes = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TvRecoveryOverlay(
                    visible = true,
                    message = "Playback interrupted",
                    hint = "Choose an action.",
                    primaryActionLabel = "Retry",
                    onPrimaryAction = { retries++ },
                    secondaryActionLabel = "Close",
                    onSecondaryAction = { closes++ },
                )
            }
        }

        composeRule.onNodeWithTag("tv-recovery-primary")
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(1, retries) }

        composeRule.onNodeWithTag("tv-recovery-primary")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("tv-recovery-secondary")
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun closeOnlyFocusesCloseAndPublishesModalStatusSemantics() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TvRecoveryOverlay(
                    visible = true,
                    message = "Recording unavailable",
                    hint = "The file cannot be played.",
                    primaryActionLabel = "Close",
                    onPrimaryAction = {},
                    liveRegionMode = LiveRegionMode.Assertive,
                )
            }
        }

        composeRule.onNodeWithText("Close").assertIsFocused()
        composeRule.onNodeWithTag("tv-recovery-overlay")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "Recording unavailable",
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                )
            )
        composeRule.onNodeWithText("Recording unavailable")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun passiveBackingIsNotFocusableOrActionable() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TvRecoveryOverlay(
                    visible = true,
                    message = "Reconnecting automatically",
                )
            }
        }

        composeRule.onNodeWithTag("tv-recovery-overlay")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
            .assert(
                SemanticsMatcher.keyNotDefined(SemanticsProperties.IsDialog)
            )
    }
}
