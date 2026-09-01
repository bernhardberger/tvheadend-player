package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SimpleTvStartConfirmationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationIsVisibleSafeFocusedAndActionable() {
        var confirmed = false

        composeRule.setContent {
            TVHeadendPlayerTheme {
                SimpleTvStartConfirmation(
                    onCancel = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeRule.onNodeWithText("Start Simple TV now?").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsFocused()
        composeRule.onNode(hasText("Start Simple TV now") and hasClickAction())
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertTrue(confirmed) }
    }
}
